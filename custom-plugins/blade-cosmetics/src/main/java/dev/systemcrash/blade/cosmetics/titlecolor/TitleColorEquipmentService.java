package dev.systemcrash.blade.cosmetics.titlecolor;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class TitleColorEquipmentService implements Listener {
    public static final String PDC_TITLE_COLOR_ID = "title_color_id";

    private final BladeCosmeticsPlugin plugin;
    private final TitleColorCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey colorKey;

    public TitleColorEquipmentService(
            BladeCosmeticsPlugin plugin,
            TitleColorCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.colorKey = new NamespacedKey(plugin, PDC_TITLE_COLOR_ID);
    }

    public void equip(Player player, String colorId) {
        repository.setEquippedTitleColor(player.getUniqueId(), colorId);
        plugin.titleEquipmentService().apply(player);
    }

    public void clear(Player player) {
        repository.setEquippedTitleColor(player.getUniqueId(), null);
        plugin.titleEquipmentService().apply(player);
    }

    public ItemStack createGuiIcon(TitleColorDefinition color, boolean owned, int price) {
        Material material = Material.matchMaterial(color.material());
        if (material == null || material.isAir()) {
            material = Material.WHITE_DYE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String preview = TitleGradient.previewMini(color.name(), color.colors());
        meta.displayName(mm("<!italic>" + preview));
        meta.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, color.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(color.description()) + "</color>"));
        lore.add(Component.empty());
        lore.add(mm("<!italic><color:#AAAAAA>пᴘᴇʙью:</color> "
                + TitleGradient.previewMini("титул", color.colors())));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый цʙᴇт:</color> <color:#4EEA65>дᴏᴄтупᴇʜ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый цʙᴇт:</color> <color:#EA4E4E>ʜᴇдᴏᴄтупᴇʜ</color>"));
            lore.add(mm("<!italic><color:#FFFFFF>ᴄтᴏимᴏᴄть:</color> <color:#4498DB>" + price
                    + "</color> <color:#AAAAAA>ᴏᴘбᴏʙ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжмитᴇ</color><color:#AAAAAA>, чтᴏбы ᴋупить</color>"));
            lore.add(Component.empty());
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component mm(String mini) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
    }

    private static String escape(String raw) {
        return raw.replace("<", "").replace(">", "");
    }
}
