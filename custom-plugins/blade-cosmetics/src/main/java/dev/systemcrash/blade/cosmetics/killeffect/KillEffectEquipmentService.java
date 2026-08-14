package dev.systemcrash.blade.cosmetics.killeffect;

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

public final class KillEffectEquipmentService implements Listener {
    public static final String PDC_KILL_EFFECT_ID = "kill_effect_id";

    private final BladeCosmeticsPlugin plugin;
    private final KillEffectCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey effectKey;

    public KillEffectEquipmentService(
            BladeCosmeticsPlugin plugin,
            KillEffectCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.effectKey = new NamespacedKey(plugin, PDC_KILL_EFFECT_ID);
    }

    public void equip(Player player, String effectId) {
        repository.setEquippedKillEffect(player.getUniqueId(), effectId);
    }

    public void clear(Player player) {
        repository.setEquippedKillEffect(player.getUniqueId(), null);
    }

    public ItemStack createGuiIcon(KillEffectDefinition effect, boolean owned, int price) {
        Material material = Material.matchMaterial(effect.material());
        if (material == null || material.isAir()) {
            material = Material.FIRE_CHARGE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<!italic><color:#FFFFFF>" + escape(effect.name()) + "</color>"));
        meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(effect.description()) + "</color>"));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый эффᴇᴋт:</color> <color:#4EEA65>дᴏᴄтупᴇʜ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый эффᴇᴋт:</color> <color:#EA4E4E>ʜᴇдᴏᴄтупᴇʜ</color>"));
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
