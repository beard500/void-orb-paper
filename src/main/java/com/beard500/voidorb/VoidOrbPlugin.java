package com.beard500.voidorb;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * void_orb — a throwable custom item for Paper 1.21.11.
 * <p>
 * Flight model has three phases: OUTBOUND (real EnderPearl entity, vanilla
 * physics, pierces enemies via cancelled hit events), LANDED (stationary Item
 * entity, 3-second pause at hit location), RETURNING (Item entity homes to
 * owner at the same speed it was thrown at). Left-click teleports the player
 * to the orb's current position at any phase.
 */
public final class VoidOrbPlugin extends JavaPlugin implements Listener, CommandExecutor {

    /** PDC key marking the item and the in-flight entity as ours. */
    private NamespacedKey markerKey;

    /** Identifier pointing at the item model shipped in the resource pack. */
    private static final NamespacedKey ITEM_MODEL_KEY =
            NamespacedKey.fromString("void_orb:void_orb");

    // Flight tuning
    private static final double MAX_RANGE = 48.0;
    private static final int OUTBOUND_HARD_CAP_TICKS = 100; // 5s ceiling for OUTBOUND
    private static final int LANDED_DURATION_TICKS = 60;    // 3s pause after landing
    private static final double ORB_DAMAGE = 9.0;
    private static final double RETURN_CATCH_DISTANCE = 1.5;

    // Cooldown tuning (ticks)
    private static final int TELEPORT_COOLDOWN_TICKS = 200;
    private static final int LEVITATION_DURATION_TICKS = 100;
    private static final int LEVITATION_COOLDOWN_TICKS = 60;

    // Per-player state
    private final Map<UUID, OrbFlight> orbsInFlight = new HashMap<>();
    private final Map<UUID, Integer> teleportCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Integer> levitationCooldownUntilTick = new HashMap<>();

    @Override
    public void onEnable() {
        this.markerKey = new NamespacedKey(this, "is_void_orb");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("voidorb").setExecutor(this);
        getLogger().info("void_orb enabled — resource pack URL + sha1 must be set in server.properties for model to render");
    }

    @Override
    public void onDisable() {
        for (OrbFlight flight : orbsInFlight.values()) {
            flight.forceEnd();
        }
        orbsInFlight.clear();
    }

    /** Build a fresh void_orb ItemStack. */
    private ItemStack createVoidOrbStack() {
        ItemStack stack = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(ITEM_MODEL_KEY);
        meta.displayName(Component.text("Void Orb", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    /** True if the given stack is one of ours. */
    private boolean isVoidOrb(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENDER_PEARL) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isOurPearl(EnderPearl pearl) {
        Byte marker = pearl.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private OrbFlight findFlightByPearl(EnderPearl pearl) {
        for (OrbFlight flight : orbsInFlight.values()) {
            if (flight.pearlEntity == pearl) return flight;
        }
        return null;
    }

    private boolean isOnTeleportCooldown(Player player) {
        Integer until = teleportCooldownUntilTick.get(player.getUniqueId());
        if (until == null) return false;
        if (getServer().getCurrentTick() >= until) {
            teleportCooldownUntilTick.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean isOnLevitationCooldown(Player player) {
        Integer until = levitationCooldownUntilTick.get(player.getUniqueId());
        if (until == null) return false;
        if (getServer().getCurrentTick() >= until) {
            levitationCooldownUntilTick.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /voidorb give [player]", NamedTextColor.GRAY));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Console must specify a player: /voidorb give <player>",
                    NamedTextColor.RED));
            return true;
        }

        ItemStack stack = createVoidOrbStack();
        target.getInventory().addItem(stack).forEach((i, leftover) ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        target.sendMessage(Component.text("A void_orb hums in your inventory.",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack stack = event.getItem();
        if (!isVoidOrb(stack)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (orbsInFlight.containsKey(uuid)) return;
        if (isOnTeleportCooldown(player)) return;

        launchOrb(player);
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!isVoidOrb(player.getInventory().getItemInMainHand())) return;

        OrbFlight flight = orbsInFlight.get(player.getUniqueId());
        if (flight == null) return;

        event.setCancelled(true);
        teleportToOrb(player, flight);
    }

    @EventHandler
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!isVoidOrb(main)) return;

        OrbFlight flight = orbsInFlight.get(player.getUniqueId());
        if (flight != null) {
            event.setCancelled(true);
            teleportToOrb(player, flight);
            return;
        }

        if (isOnLevitationCooldown(player)) return;
        if (!(event.getAttacked() instanceof LivingEntity target)) return;

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION, LEVITATION_DURATION_TICKS, 0));
        levitationCooldownUntilTick.put(player.getUniqueId(),
                getServer().getCurrentTick() + LEVITATION_COOLDOWN_TICKS);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                target.getLocation().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS,
                0.7f, 1.4f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!isOurPearl(pearl)) return;

        event.setCancelled(true); // no vanilla teleport, ever

        OrbFlight flight = findFlightByPearl(pearl);
        if (flight == null || flight.transitioning) return;

        Entity hitEntity = event.getHitEntity();
        if (hitEntity instanceof LivingEntity target
                && !target.getUniqueId().equals(flight.ownerId)) {
            if (flight.hits.add(target.getUniqueId())) {
                Player owner = getServer().getPlayer(flight.ownerId);
                DamageSource src = DamageSource.builder(DamageType.MAGIC)
                        .withDirectEntity(pearl)
                        .withCausingEntity(owner)
                        .build();
                target.damage(ORB_DAMAGE, src);
                target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        target.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0.05);
            }
            return; // pearl continues piercing
        }

        if (event.getHitBlock() != null || hitEntity != null) {
            flight.transitionToLanded(pearl.getLocation());
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            event.getDrops().add(createVoidOrbStack());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    private void cleanupPlayer(Player player) {
        OrbFlight flight = orbsInFlight.remove(player.getUniqueId());
        if (flight != null) flight.forceEnd();
    }

    private void launchOrb(Player player) {
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        pearl.setItem(createVoidOrbStack());
        pearl.getPersistentDataContainer()
                .set(markerKey, PersistentDataType.BYTE, (byte) 1);

        player.getWorld().playSound(player.getLocation(),
                Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS,
                0.5f, 1.6f + (float) (Math.random() * 0.2));

        OrbFlight flight = new OrbFlight(player.getUniqueId(), pearl);
        orbsInFlight.put(player.getUniqueId(), flight);
        flight.runTaskTimer(this, 1L, 1L);
    }

    private void teleportToOrb(Player player, OrbFlight flight) {
        Location orbLoc = flight.getCurrentLocation();
        Location dest = new Location(orbLoc.getWorld(), orbLoc.getX(), orbLoc.getY(), orbLoc.getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch());

        player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);

        dest.getWorld().spawnParticle(Particle.PORTAL, dest, 40, 0.3, 0.5, 0.3, 0.5);
        dest.getWorld().spawnParticle(Particle.REVERSE_PORTAL, dest, 20, 0.2, 0.3, 0.2, 0.2);
        dest.getWorld().playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                SoundCategory.PLAYERS, 0.8f, 0.6f);

        flight.forceEnd();
        orbsInFlight.remove(player.getUniqueId());

        teleportCooldownUntilTick.put(player.getUniqueId(),
                getServer().getCurrentTick() + TELEPORT_COOLDOWN_TICKS);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isVoidOrb(mainHand)) {
            player.setCooldown(mainHand, TELEPORT_COOLDOWN_TICKS);
        }
    }

    /**
     * Drives an orb from throw to return. One instance per in-flight orb.
     * OUTBOUND: real EnderPearl with vanilla physics; piercing via cancelled hit events.
     * LANDED: stationary Item entity, 3-second pause at hit location.
     * RETURNING: Item entity homes to owner at recorded throw speed.
     */
    private final class OrbFlight extends BukkitRunnable {
        enum Phase { OUTBOUND, LANDED, RETURNING }

        final UUID ownerId;
        EnderPearl pearlEntity;   // non-null during OUTBOUND
        Item itemEntity;          // non-null during LANDED + RETURNING
        final Set<UUID> hits = new HashSet<>();
        Phase phase = Phase.OUTBOUND;
        int ticksAlive = 0;
        int landedTicksRemaining = 0;
        boolean transitioning = false;
        boolean ended = false;
        final Location launchLocation;
        final double throwSpeedMagnitude;

        OrbFlight(UUID ownerId, EnderPearl pearl) {
            this.ownerId = ownerId;
            this.pearlEntity = pearl;
            this.launchLocation = pearl.getLocation().clone();
            // vanilla pearl velocity at launch = throw speed we reuse for RETURNING
            double mag = pearl.getVelocity().length();
            this.throwSpeedMagnitude = mag > 0.1 ? mag : 1.5;
        }

        Location getCurrentLocation() {
            if (phase == Phase.OUTBOUND && pearlEntity != null && pearlEntity.isValid()) {
                return pearlEntity.getLocation();
            }
            if (itemEntity != null && itemEntity.isValid()) {
                return itemEntity.getLocation();
            }
            return launchLocation;
        }

        void transitionToLanded(Location hitLoc) {
            if (transitioning) return;
            transitioning = true;

            if (pearlEntity != null && pearlEntity.isValid()) {
                pearlEntity.remove();
            }
            pearlEntity = null;

            World world = hitLoc.getWorld();
            Item item = world.dropItem(hitLoc, createVoidOrbStack());
            item.setGravity(false);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setPersistent(false);
            item.setVelocity(new Vector(0, 0, 0));
            item.getPersistentDataContainer()
                    .set(markerKey, PersistentDataType.BYTE, (byte) 1);

            this.itemEntity = item;
            this.phase = Phase.LANDED;
            this.landedTicksRemaining = LANDED_DURATION_TICKS;

            world.spawnParticle(Particle.REVERSE_PORTAL, hitLoc,
                    20, 0.2, 0.2, 0.2, 0.15);
            world.playSound(hitLoc, Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                    SoundCategory.NEUTRAL, 0.6f, 0.8f);
        }

        @Override
        public void run() {
            if (ended) return;

            Player owner = getServer().getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || owner.isDead()) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            World orbWorld = (pearlEntity != null && pearlEntity.isValid())
                    ? pearlEntity.getWorld()
                    : (itemEntity != null && itemEntity.isValid() ? itemEntity.getWorld() : null);
            if (orbWorld == null || !owner.getWorld().equals(orbWorld)) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            switch (phase) {
                case OUTBOUND -> tickOutbound();
                case LANDED -> tickLanded();
                case RETURNING -> tickReturning(owner);
            }
        }

        private void tickOutbound() {
            if (pearlEntity == null || !pearlEntity.isValid() || pearlEntity.isDead()) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            ticksAlive++;
            Location current = pearlEntity.getLocation();

            if (ticksAlive > OUTBOUND_HARD_CAP_TICKS) {
                transitionToLanded(current);
                return;
            }
            if (current.distance(launchLocation) > MAX_RANGE) {
                transitionToLanded(current);
                return;
            }

            current.getWorld().spawnParticle(Particle.PORTAL, current,
                    2, 0.05, 0.05, 0.05, 0.02);
        }

        private void tickLanded() {
            if (itemEntity == null || !itemEntity.isValid()) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            itemEntity.setVelocity(new Vector(0, 0, 0));
            landedTicksRemaining--;

            if (landedTicksRemaining % 10 == 0) {
                itemEntity.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        itemEntity.getLocation().add(0, 0.3, 0),
                        4, 0.15, 0.15, 0.15, 0.02);
            }

            if (landedTicksRemaining <= 0) {
                phase = Phase.RETURNING;
            }
        }

        private void tickReturning(Player owner) {
            if (itemEntity == null || !itemEntity.isValid()) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            Location current = itemEntity.getLocation();
            Location target = owner.getEyeLocation();
            Vector toOwner = target.toVector().subtract(current.toVector());
            double dist = toOwner.length();

            if (dist < RETURN_CATCH_DISTANCE) {
                current.getWorld().playSound(current, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.8f, 1.2f);
                current.getWorld().spawnParticle(Particle.REVERSE_PORTAL, current,
                        20, 0.2, 0.2, 0.2, 0.15);
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            Vector velocity = toOwner.normalize().multiply(throwSpeedMagnitude);
            itemEntity.setVelocity(velocity);

            current.getWorld().spawnParticle(Particle.PORTAL, current,
                    2, 0.05, 0.05, 0.05, 0.02);
        }

        void forceEnd() {
            if (ended) return;
            ended = true;
            if (pearlEntity != null && pearlEntity.isValid()) {
                pearlEntity.remove();
            }
            if (itemEntity != null && itemEntity.isValid()) {
                itemEntity.remove();
            }
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // task not yet scheduled or already cancelled
            }
        }
    }
}
