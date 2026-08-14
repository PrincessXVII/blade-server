package dev.systemcrash.blade.cosmetics.title;

import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class TitleService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final TitleCatalog catalog;
    private final CosmeticsRepository repository;
    private final OrbsManager orbs;
    private final TitleEquipmentService equipment;

    public TitleService(
            BladeCosmeticsPlugin plugin,
            TitleCatalog catalog,
            CosmeticsRepository repository,
            OrbsManager orbs,
            TitleEquipmentService equipment
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

    public ClickResult clickTitle(Player player, String titleId) {
        var title = catalog.find(titleId);
        if (title.isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (repository.ownsTitle(player.getUniqueId(), titleId)) {
            equipment.equip(player, titleId);
            return ClickResult.EQUIPPED;
        }
        int price = repository.titlePriceOf(titleId);
        if (!orbs.trySpend(player.getUniqueId(), price)) {
            sendNotEnough(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.NOT_ENOUGH_ORBS;
        }
        repository.grantTitle(player.getUniqueId(), titleId);
        equipment.equip(player, titleId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return ClickResult.PURCHASED;
    }

    public void clearTitle(Player player) {
        equipment.clear(player);
    }

    private void sendNotEnough(Player player) {
        String line1 = plugin.getConfig().getString("messages.not-enough-orbs-1", "");
        String line2 = plugin.getConfig().getString("messages.not-enough-orbs-2", "");
        player.sendMessage(MM.deserialize(line1));
        player.sendMessage(MM.deserialize(line2));
    }
}
