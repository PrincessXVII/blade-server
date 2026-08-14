package dev.systemcrash.blade.cosmetics.cage;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Optional;

public final class CageService {
    public static final String PDC_CAGE_ID = "cage_id";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final CageCatalog catalog;
    private final CosmeticsRepository repository;

    public CageService(BladeCosmeticsPlugin plugin, CageCatalog catalog, CosmeticsRepository repository) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
    }

    public enum ClickResult {
        EQUIPPED,
        LOCKED,
        CLEARED,
        UNKNOWN
    }

    public ClickResult clickCage(Player player, String cageId) {
        Optional<CageDefinition> cage = catalog.find(cageId);
        if (cage.isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (!canUse(player, cage.get())) {
            player.sendMessage(MM.deserialize(
                    "<!italic><color:#EA4E4E>Эта клетка тебе недоступна.</color>"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.LOCKED;
        }
        repository.setEquippedCage(player.getUniqueId(), cageId);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        player.sendMessage(MM.deserialize(
                "<!italic><color:#AAAAAA>Клетка </color>"
                        + cage.get().display()
                        + "<color:#AAAAAA> выбрана. Сменится при следующем заходе в митапы.</color>"));
        return ClickResult.EQUIPPED;
    }

    public void clearCage(Player player) {
        repository.setEquippedCage(player.getUniqueId(), null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
        player.sendMessage(MM.deserialize(
                "<!italic><color:#AAAAAA>Выбрана стандартная стеклянная клетка.</color>"));
    }

    public boolean canUse(Player player, CageDefinition cage) {
        if (player.hasPermission("blade.cosmetics.cage.*")
                || player.hasPermission("blade.cosmetics.cage." + cage.id())) {
            return true;
        }
        return rankWeight(player) >= cage.minWeight();
    }

    /** Cages the player can currently equip (filtered for GUI). */
    public java.util.List<CageDefinition> availableCages(Player player) {
        return catalog.all().stream().filter(c -> canUse(player, c)).toList();
    }

    public boolean hasCageCategory(Player player) {
        return !availableCages(player).isEmpty();
    }

    public Optional<CageDefinition> equipped(Player player) {
        return repository.equippedCage(player.getUniqueId()).flatMap(catalog::find);
    }

    /** Schematic file name for meetups paste, or empty for default glass. */
    public Optional<String> equippedSchematic(java.util.UUID uuid) {
        return repository.equippedCage(uuid)
                .flatMap(catalog::find)
                .map(CageDefinition::schematic);
    }

    public ItemStack createGuiIcon(CageDefinition cage, Player viewer) {
        Material material = Material.matchMaterial(cage.material());
        if (material == null || material.isAir()) {
            material = Material.TRIAL_SPAWNER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(cage.display())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, PDC_CAGE_ID),
                PersistentDataType.STRING,
                cage.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        boolean equipped = repository.equippedCage(viewer.getUniqueId()).map(cage.id()::equals).orElse(false);
        var lore = new ArrayList<Component>();
        lore.add(MM.deserialize("<!italic><color:#AAAAAA>" + escape(cage.description()) + "</color>"));
        lore.add(Component.empty());
        if (equipped) {
            lore.add(MM.deserialize("<!italic><color:#5FE2C5>выбрана сейчас</color>"));
        } else {
            lore.add(MM.deserialize(
                    "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы выбрать</color>"));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static int rankWeight(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                return 0;
            }
            Group group = lp.getGroupManager().getGroup(user.getPrimaryGroup());
            if (group == null) {
                return 0;
            }
            return group.getWeight().orElse(0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
