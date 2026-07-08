package dev.systemcrash.spawn.hub;

import dev.systemcrash.spawn.ServerSpawnControlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class HubMenuService {
    private final ServerSpawnControlPlugin plugin;

    public HubMenuService(ServerSpawnControlPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        HubMenuHolder holder = new HubMenuHolder(HubMenuType.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, HubMenuLayout.MAIN_MENU_SIZE, " ");

        for (int slot : HubMenuLayout.allMainMenuSlots()) {
            if (HubMenuLayout.isMeetupsSlot(slot)) {
                inventory.setItem(slot, HubItems.meetupsTile(HubMenuLayout.meetupsTileIndex(slot)));
            } else if (HubMenuLayout.isBkbSlot(slot)) {
                inventory.setItem(slot, HubItems.bkbTile(HubMenuLayout.bkbTileIndex(slot)));
            } else if (HubMenuLayout.isSmpSlot(slot)) {
                inventory.setItem(slot, HubItems.smpTile(HubMenuLayout.smpTileIndex(slot)));
            }
        }

        player.openInventory(inventory);
    }

    public void openMeetupsArenaMenu(Player player) {
        HubMenuHolder holder = new HubMenuHolder(HubMenuType.MEETUPS_ARENAS);
        Inventory inventory = Bukkit.createInventory(holder, HubMenuLayout.ARENA_MENU_SIZE, " ");

        for (int arenaIndex = 0; arenaIndex < HubMenuLayout.ARENA_COUNT; arenaIndex++) {
            if (isArenaAvailable(arenaIndex)) {
                inventory.setItem(arenaIndex, HubItems.availableArenaItem());
            } else {
                inventory.setItem(arenaIndex, HubItems.unavailableArenaItem());
            }
        }

        player.openInventory(inventory);
    }

    public boolean isArenaAvailable(int arenaIndex) {
        if (arenaIndex != 0) {
            return false;
        }
        Plugin meetups = Bukkit.getPluginManager().getPlugin("BladeMeetups");
        if (meetups == null || !meetups.isEnabled()) {
            return false;
        }
        try {
            Object matchManager = meetups.getClass().getMethod("matchManager").invoke(meetups);
            Method hasActiveMatch = matchManager.getClass().getMethod("hasActiveMatch");
            boolean active = (boolean) hasActiveMatch.invoke(matchManager);
            if (active) {
                return false;
            }
            Object arenaManager = meetups.getClass().getMethod("arenaManager").invoke(meetups);
            Method isResetting = arenaManager.getClass().getMethod("isResetting");
            return !(boolean) isResetting.invoke(arenaManager);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Failed to query meetups arena state: " + ex.getMessage());
            return false;
        }
    }

    public void joinMeetupsArena(Player player, int arenaIndex) {
        if (arenaIndex != 0) {
            player.sendMessage("§cЭта арена пока недоступна.");
            return;
        }
        if (!isArenaAvailable(arenaIndex)) {
            player.sendMessage("§cНа этой арене уже идёт игра.");
            return;
        }
        Plugin meetups = Bukkit.getPluginManager().getPlugin("BladeMeetups");
        if (meetups == null || !meetups.isEnabled()) {
            player.sendMessage("§cMeetups сейчас недоступны.");
            return;
        }
        try {
            Object queue = meetups.getClass().getMethod("meetupQueue").invoke(meetups);
            Method join = queue.getClass().getMethod("join", Player.class);
            join.invoke(queue, player);
            player.closeInventory();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Failed to join meetups queue: " + ex.getMessage());
            player.sendMessage("§cНе удалось войти в очередь Meetups.");
        }
    }
}
