package com.beard500.voidorb;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import org.bukkit.util.RayTraceResult;
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
 * Right-click launches a piercing orb that damages LivingEntities along its path
 * and returns to the owner like a loyalty trident. Left-click while the orb is
 * in flight teleports the player to the orb's current position. Left-click on
 * an entity with no orb in flight levitates the target. Guaranteed drop from
 * the Ender Dragon; infinite durability (never consumed on throw).
 */
public final class VoidOrbPlugin extends JavaPlugin implements Listener, CommandExecutor {

    /** PDC key marking both the item and the flight visual as ours. */
    private NamespacedKey markerKey;

    /** Identifier pointing at the item model shipped in the resource pack. */
    private static final NamespacedKey ITEM_MODEL_KEY =
            NamespacedKey.fromString("void_orb:void_orb");

    // Flight tuning
    private static final double MAX_RANGE = 48.0;
    private static final int MAX_OUTBOUND_TICKS = 60;
    private static final int HARD_CAP_TICKS = 200;
    private static final double THROW_SPEED = 1.8;
    private static final double RETURN_SPEED = 1.8;
    private static final double ORB_DAMAGE = 9.0;
    private static final double PIERCE_RADIUS = 1.5;
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

        launchOrb(player, stack);
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

    private void launchOrb(Player player, ItemStack heldStack) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().multiply(THROW_SPEED);

        Item itemEntity = player.getWorld().dropItem(eye, createVoidOrbStack());
        itemEntity.setGravity(false);
        itemEntity.setPickupDelay(Integer.MAX_VALUE);
        itemEntity.setPersistent(false);
        itemEntity.setVelocity(dir);
        itemEntity.getPersistentDataContainer()
                .set(markerKey, PersistentDataType.BYTE, (byte) 1);

        player.getWorld().playSound(player.getLocation(),
                Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS,
                0.5f, 1.6f + (float) (Math.random() * 0.2));

        OrbFlight flight = new OrbFlight(player.getUniqueId(), itemEntity, dir);
        orbsInFlight.put(player.getUniqueId(), flight);
        flight.runTaskTimer(this, 1L, 1L);
    }

    private void teleportToOrb(Player player, OrbFlight flight) {
        Location orbLoc = flight.itemEntity.getLocation();
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
     * OUTBOUND: orb flies in the throw direction, piercing LivingEntities.
     * RETURNING: orb homes back to the owner.
     */
    private final class OrbFlight extends BukkitRunnable {
        private static final int STATE_OUTBOUND = 0;
        private static final int STATE_RETURNING = 1;

        private final UUID ownerId;
        final Item itemEntity;
        private final Set<UUID> hits = new HashSet<>();
        private Vector velocity;
        private int state = STATE_OUTBOUND;
        private int ticksAlive = 0;
        private double blocksTraveled = 0;
        private Location lastLocation;
        private boolean ended = false;

        OrbFlight(UUID ownerId, Item itemEntity, Vector initialVelocity) {
            this.ownerId = ownerId;
            this.itemEntity = itemEntity;
            this.velocity = initialVelocity.clone();
            this.lastLocation = itemEntity.getLocation();
        }

        @Override
        public void run() {
            if (ended) return;

            Player owner = getServer().getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || owner.isDead()
                    || !owner.getWorld().equals(itemEntity.getWorld())
                    || !itemEntity.isValid()) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            ticksAlive++;
            if (ticksAlive > HARD_CAP_TICKS) {
                forceEnd();
                orbsInFlight.remove(ownerId);
                return;
            }

            Location current = itemEntity.getLocation();
            World world = current.getWorld();

            // Block collision — raycast the segment actually traveled since last tick.
            if (state == STATE_OUTBOUND) {
                Vector travel = current.toVector().subtract(lastLocation.toVector());
                double travelLen = travel.length();
                if (travelLen > 0.001) {
                    RayTraceResult blockHit = world.rayTraceBlocks(
                            lastLocation, travel.clone().normalize(), travelLen,
                            FluidCollisionMode.NEVER, true);
                    if (blockHit != null && blockHit.getHitBlock() != null) {
                        state = STATE_RETURNING;
                    }
                }
            }

            // Pierce damage — one hit per entity per flight.
            for (LivingEntity candidate : world.getNearbyLivingEntities(
                    current, PIERCE_RADIUS,
                    le -> !le.getUniqueId().equals(ownerId) && !hits.contains(le.getUniqueId()))) {
                hits.add(candidate.getUniqueId());
                DamageSource src = DamageSource.builder(DamageType.MAGIC)
                        .withDirectEntity(itemEntity)
                        .withCausingEntity(owner)
                        .build();
                candidate.damage(ORB_DAMAGE, src);
                world.spawnParticle(Particle.REVERSE_PORTAL,
                        candidate.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0.05);
            }

            blocksTraveled += current.distance(lastLocation);
            if (state == STATE_OUTBOUND
                    && (blocksTraveled > MAX_RANGE || ticksAlive > MAX_OUTBOUND_TICKS)) {
                state = STATE_RETURNING;
            }

            world.spawnParticle(Particle.PORTAL, current, 3, 0.05, 0.05, 0.05, 0.05);

            if (state == STATE_RETURNING) {
                Location target = owner.getEyeLocation();
                Vector toOwner = target.toVector().subtract(current.toVector());
                double dist = toOwner.length();
                if (dist < RETURN_CATCH_DISTANCE) {
                    world.playSound(current, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            SoundCategory.PLAYERS, 0.8f, 1.2f);
                    world.spawnParticle(Particle.REVERSE_PORTAL, current,
                            20, 0.2, 0.2, 0.2, 0.15);
                    forceEnd();
                    orbsInFlight.remove(ownerId);
                    return;
                }
                velocity = toOwner.normalize().multiply(RETURN_SPEED);
            }

            itemEntity.setVelocity(velocity);
            lastLocation = current.clone();
        }

        void forceEnd() {
            if (ended) return;
            ended = true;
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
