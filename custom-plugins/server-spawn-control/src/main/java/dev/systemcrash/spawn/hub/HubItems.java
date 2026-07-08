package dev.systemcrash.spawn.hub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class HubItems {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Component PLACEHOLDER_NAME = Component.text(" ");
    private static final Component CHOOSE_SERVER_NAME = MINI_MESSAGE.deserialize(
            "<!italic><#FF9E64>B<#F9936C>ы<#F38975>б<#ED7E7D>ᴘ<#E87486>ᴀ<#E2698E>т<#DC5F97>ь<#D6549F> "
                    + "<#C751A6>ᴄ<#B84EAD>ᴇ<#A94BB4>ᴘ<#9B48BC>ʙ<#8C45C3>ᴇ<#7D42CA>ᴘ<#6E3FD1> "
                    + "<#FFFFFF>>> Mᴇʜю"
    ).decoration(TextDecoration.ITALIC, false);

    private HubItems() {
    }

    public static ItemStack chooseServerItem() {
        return icon(HubItemIds.CHOOSE_SERVER, CHOOSE_SERVER_NAME);
    }

    public static ItemStack meetupsTile(int tileIndex) {
        int row = tileIndex / 3;
        int col = tileIndex % 3;
        return icon(HubItemIds.meetupsTile(row, col), PLACEHOLDER_NAME);
    }

    public static ItemStack bkbTile(int tileIndex) {
        int row = tileIndex / 4;
        int col = tileIndex % 4;
        return icon(HubItemIds.bkbTile(row, col), PLACEHOLDER_NAME);
    }

    public static ItemStack smpTile(int tileIndex) {
        int row = tileIndex / 3;
        int col = tileIndex % 3;
        return icon(HubItemIds.smpTile(row, col), PLACEHOLDER_NAME);
    }

    public static ItemStack availableArenaItem() {
        return icon(HubItemIds.ARENA_AVAILABLE, PLACEHOLDER_NAME);
    }

    public static ItemStack unavailableArenaItem() {
        return icon(HubItemIds.ARENA_UNAVAILABLE, PLACEHOLDER_NAME);
    }

    public static boolean isChooseServerItem(ItemStack item) {
        return matches(item, HubItemIds.CHOOSE_SERVER);
    }

    public static void giveChooseServerItem(Player player) {
        ItemStack item = chooseServerItem();
        player.getInventory().setItem(0, item);
    }

    private static ItemStack icon(int modelData, Component name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.setCustomModelData(modelData);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean matches(ItemStack item, int modelData) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == modelData;
    }
}
