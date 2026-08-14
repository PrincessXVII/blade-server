package dev.systemcrash.blade.cosmetics.titlecolor;

import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class TitleColorService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final TitleColorCatalog catalog;
    private final CosmeticsRepository repository;
    private final OrbsManager orbs;
    private final TitleColorEquipmentService equipment;

    public TitleColorService(
            BladeCosmeticsPlugin plugin,
            TitleColorCatalog catalog,
            CosmeticsRepository repository,
            OrbsManager orbs,
            TitleColorEquipmentService equipment
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.orbs = orbs;
        this.equipment = equipment;
    }

    public enum ClickResult {
        EQUIPPED,
        PURCHASED,
        NOT_ENOUGH_ORBS,
        UNKNOWN
    }

    public ClickResult clickColor(Player player, String colorId) {
        if (catalog.find(colorId).isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (repository.ownsTitleColor(player.getUniqueId(), colorId)) {
            equipment.equip(player, colorId);
            return ClickResult.EQUIPPED;
        }
        int price = repository.titleColorPriceOf(colorId);
        if (!orbs.trySpend(player.getUniqueId(), price)) {
            player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-1", "")));
            player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-2", "")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.NOT_ENOUGH_ORBS;
        }
        repository.grantTitleColor(player.getUniqueId(), colorId);
        equipment.equip(player, colorId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return ClickResult.PURCHASED;
    }

    public void clearColor(Player player) {
        equipment.clear(player);
    }
}
