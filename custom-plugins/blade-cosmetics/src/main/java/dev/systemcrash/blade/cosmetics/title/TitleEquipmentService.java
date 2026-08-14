package dev.systemcrash.blade.cosmetics.title;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorDefinition;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleGradient;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TitleEquipmentService implements Listener {
    public static final String PDC_TITLE_ID = "title_id";
    public static final int SUFFIX_PRIORITY = 100;

    private final BladeCosmeticsPlugin plugin;
    private final TitleCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey titleKey;

    public TitleEquipmentService(
            BladeCosmeticsPlugin plugin,
            TitleCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.titleKey = new NamespacedKey(plugin, PDC_TITLE_ID);
    }

    public void equip(Player player, String titleId) {
        repository.setEquippedTitle(player.getUniqueId(), titleId);
        apply(player);
    }

    public void clear(Player player) {
        repository.setEquippedTitle(player.getUniqueId(), null);
        clearLuckPermsSuffix(player.getUniqueId());
    }

    public void apply(Player player) {
        Optional<String> equipped = repository.equippedTitle(player.getUniqueId());
        if (equipped.isEmpty()) {
            clearLuckPermsSuffix(player.getUniqueId());
            return;
        }
        catalog.find(equipped.get()).ifPresentOrElse(
                title -> setLuckPermsSuffix(player.getUniqueId(), title),
                () -> clearLuckPermsSuffix(player.getUniqueId())
        );
    }

    public ItemStack createGuiIcon(TitleDefinition title, boolean owned, int price) {
        Material material = Material.matchMaterial(title.material());
        if (material == null || material.isAir()) {
            material = Material.NAME_TAG;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<!italic><color:" + title.color() + ">" + escape(title.display()) + "</color>"));
        meta.getPersistentDataContainer().set(titleKey, PersistentDataType.STRING, title.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(title.description()) + "</color>"));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый титул:</color> <color:#4EEA65>дᴏᴄтупᴇʜ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый титул:</color> <color:#EA4E4E>ʜᴇдᴏᴄтупᴇʜ</color>"));
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

    private void setLuckPermsSuffix(UUID uuid, TitleDefinition title) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().modifyUser(uuid, user -> {
            clearCosmeticSuffixes(user);
            String colored = buildColoredTitle(uuid, title.display());
            user.data().add(SuffixNode.builder(" " + colored, SUFFIX_PRIORITY).build());
        });
    }

    private String buildColoredTitle(UUID uuid, String display) {
        Optional<String> colorId = repository.equippedTitleColor(uuid);
        if (colorId.isPresent()) {
            Optional<TitleColorDefinition> color = plugin.titleColorCatalog().find(colorId.get());
            if (color.isPresent()) {
                List<String> stops = color.get().colors();
                if (stops != null && !stops.isEmpty()) {
                    return TitleGradient.colorizeLegacy(display, stops);
                }
            }
        }
        return "&f" + display;
    }

    private void clearLuckPermsSuffix(UUID uuid) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().modifyUser(uuid, this::clearCosmeticSuffixes);
    }

    private void clearCosmeticSuffixes(User user) {
        user.data().clear(NodeType.SUFFIX.predicate(n -> n.getPriority() == SUFFIX_PRIORITY));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 20L);
    }

    private static Component mm(String mini) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
    }

    private static String escape(String raw) {
        return raw.replace("<", "").replace(">", "");
    }
}
