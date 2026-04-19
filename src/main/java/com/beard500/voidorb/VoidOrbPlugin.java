package com.beard500.voidorb;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * void_orb — a throwable custom item for Paper 1.21.11.
 * <p>
 * The item is a real {@link Material#ENDER_PEARL} stack tagged with a PDC key to
 * identify it as ours, plus a {@code minecraft:item_model} component pointing at
 * our custom model shipped via the server's resource pack. The client sees an
 * ender-pearl-class ItemStack carrying a different model — no client mod needed.
 * <p>
 * Throw behavior: right-click spawns a real ender pearl entity (also PDC-tagged),
 * with cosmetic sound. On impact we cancel the vanilla hit, spawn portal particles,
 * and remove the pearl — so there's no teleport and no fall damage.
 */
public final class VoidOrbPlugin extends JavaPlugin implements Listener, CommandExecutor {

    /** PDC key marking both the item and the thrown projectile as ours. */
    private NamespacedKey markerKey;

    /** Identifier pointing at the item model shipped in the resource pack. */
    private static final NamespacedKey ITEM_MODEL_KEY =
            NamespacedKey.fromString("void_orb:void_orb");

    /** Throw cooldown in ticks (10 = half a second). */
    private static final int COOLDOWN_TICKS = 10;

    /** Throw velocity multiplier (higher = faster pearl). */
    private static final double THROW_VELOCITY = 1.5D;

    @Override
    public void onEnable() {
        this.markerKey = new NamespacedKey(this, "is_void_orb");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("voidorb").setExecutor(this);
        getLogger().info("void_orb enabled — resource pack URL + sha1 must be set in server.properties for model to render");
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
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack stack = event.getItem();
        if (!isVoidOrb(stack)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (player.hasCooldown(stack)) return;

        Vector direction = player.getEyeLocation().getDirection().multiply(THROW_VELOCITY);
        EnderPearl pearl = player.launchProjectile(EnderPearl.class, direction);
        pearl.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pearl.setItem(createVoidOrbStack());

        player.getWorld().playSound(player.getLocation(),
                Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS,
                0.5f, 1.6f + (float) (Math.random() * 0.2));

        player.setCooldown(stack, COOLDOWN_TICKS);

        if (player.getGameMode().name().equals("CREATIVE") == false) {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        Byte marker = pearl.getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return;

        event.setCancelled(true);

        var loc = pearl.getLocation();
        pearl.getWorld().spawnParticle(Particle.PORTAL,
                loc, 30, 0.2, 0.2, 0.2, 0.4);
        pearl.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                loc, 15, 0.1, 0.1, 0.1, 0.15);
        pearl.getWorld().playSound(loc,
                Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.NEUTRAL,
                0.8f, 0.6f);

        pearl.remove();
    }
}
