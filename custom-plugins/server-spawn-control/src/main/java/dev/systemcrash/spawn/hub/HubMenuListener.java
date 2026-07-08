package dev.systemcrash.spawn.hub;

import dev.systemcrash.spawn.OperatorModeSupport;
import dev.systemcrash.spawn.ServerSpawnControlPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class HubMenuListener implements Listener {
    private static final String LOBBY_WORLD = "lobby";

    private final ServerSpawnControlPlugin plugin;
    private final HubMenuService menuService;

    public HubMenuListener(ServerSpawnControlPlugin plugin, HubMenuService menuService) {
        this.plugin = plugin;
        this.menuService = menuService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!LOBBY_WORLD.equals(player.getWorld().getName())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && LOBBY_WORLD.equals(player.getWorld().getName())) {
                HubItems.giveChooseServerItem(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!LOBBY_WORLD.equals(player.getWorld().getName())) {
            return;
        }
        ItemStack item = event.getItem();
        if (!HubItems.isChooseServerItem(item)) {
            return;
        }
        event.setCancelled(true);
        menuService.openMainMenu(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof HubMenuHolder holder) {
            event.setCancelled(true);
            handleMenuClick(player, holder, event.getRawSlot(), top.getSize());
            return;
        }

        if (!LOBBY_WORLD.equals(player.getWorld().getName()) || OperatorModeSupport.preservesGameMode(player)) {
            return;
        }

        if (event.getRawSlot() == 0 && HubItems.isChooseServerItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
        if (event.getRawSlot() == 0 && HubItems.isChooseServerItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof HubMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (!LOBBY_WORLD.equals(player.getWorld().getName()) || OperatorModeSupport.preservesGameMode(player)) {
            return;
        }
        if (event.getRawSlots().contains(0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!LOBBY_WORLD.equals(player.getWorld().getName()) || OperatorModeSupport.preservesGameMode(player)) {
            return;
        }
        if (HubItems.isChooseServerItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!LOBBY_WORLD.equals(player.getWorld().getName()) || OperatorModeSupport.preservesGameMode(player)) {
            return;
        }
        if (HubItems.isChooseServerItem(event.getMainHandItem()) || HubItems.isChooseServerItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    private void handleMenuClick(Player player, HubMenuHolder holder, int rawSlot, int topSize) {
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }
        if (holder.type() == HubMenuType.MAIN) {
            if (HubMenuLayout.isMeetupsSlot(rawSlot)) {
                menuService.openMeetupsArenaMenu(player);
            } else if (HubMenuLayout.isBkbSlot(rawSlot)) {
                player.sendMessage("§7Battleroyale скоро будет доступен.");
            } else if (HubMenuLayout.isSmpSlot(rawSlot)) {
                player.sendMessage("§7SMP скоро будет доступен.");
            }
            return;
        }
        if (holder.type() == HubMenuType.MEETUPS_ARENAS) {
            menuService.joinMeetupsArena(player, rawSlot);
        }
    }
}
