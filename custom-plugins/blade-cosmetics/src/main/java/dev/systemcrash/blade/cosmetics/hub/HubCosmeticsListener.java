package dev.systemcrash.blade.cosmetics.hub;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.gui.CosmeticsGuiService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class HubCosmeticsListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final CosmeticsGuiService guiService;

    public HubCosmeticsListener(BladeCosmeticsPlugin plugin, CosmeticsGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public static ItemStack createHubItem(BladeCosmeticsPlugin plugin) {
        int cmd = plugin.getConfig().getInt("items.hub-cosmetics", 7003);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(MM.deserialize(
                "<!italic><gradient:#FF00C8:#DB00FF:#ff00c8>Kᴏᴄмᴇтиᴋᴀ</gradient>"
                        + " <color:#AAAAAA>»</color> <color:#FFFFFF>Mᴇʜю</color>"
        ));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHubItem(BladeCosmeticsPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        int cmd = plugin.getConfig().getInt("items.hub-cosmetics", 7003);
        return item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == cmd;
    }

    public void giveIfLobby(Player player) {
        String hubWorld = plugin.getConfig().getString("hub-world", "lobby");
        if (!hubWorld.equalsIgnoreCase(player.getWorld().getName())) {
            return;
        }
        int slot = plugin.getConfig().getInt("hub-slot", 1);
        player.getInventory().setItem(slot, createHubItem(plugin));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Same/next tick — SSC also gives hub item; this is a short fallback only.
        plugin.getServer().getScheduler().runTask(plugin, () -> giveIfLobby(event.getPlayer()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> giveIfLobby(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isHubItem(plugin, event.getItem())) {
            return;
        }
        String hubWorld = plugin.getConfig().getString("hub-world", "lobby");
        if (!hubWorld.equalsIgnoreCase(player.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        guiService.openMain(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String hubWorld = plugin.getConfig().getString("hub-world", "lobby");
        if (!hubWorld.equalsIgnoreCase(player.getWorld().getName())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        int slot = plugin.getConfig().getInt("hub-slot", 1);
        if (event.getRawSlot() == slot && isHubItem(plugin, event.getCurrentItem())) {
            event.setCancelled(true);
        }
        if (event.getHotbarButton() == slot) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isHubItem(plugin, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isHubItem(plugin, event.getMainHandItem()) || isHubItem(plugin, event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
