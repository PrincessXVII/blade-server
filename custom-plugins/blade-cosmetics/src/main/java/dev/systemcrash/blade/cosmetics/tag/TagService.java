package dev.systemcrash.blade.cosmetics.tag;

import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class TagService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final TagCatalog catalog;
    private final CosmeticsRepository repository;
    private final OrbsManager orbs;
    private final TagEquipmentService equipment;

    public TagService(
            BladeCosmeticsPlugin plugin,
            TagCatalog catalog,
            CosmeticsRepository repository,
            OrbsManager orbs,
            TagEquipmentService equipment
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

    public ClickResult clickTag(Player player, String tagId) {
        var tag = catalog.find(tagId);
        if (tag.isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (repository.ownsTag(player.getUniqueId(), tagId)) {
            equipment.equip(player, tagId);
            return ClickResult.EQUIPPED;
        }
        int price = repository.tagPriceOf(tagId);
        if (!orbs.trySpend(player.getUniqueId(), price)) {
            sendNotEnough(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.NOT_ENOUGH_ORBS;
        }
        repository.grantTag(player.getUniqueId(), tagId);
        equipment.equip(player, tagId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return ClickResult.PURCHASED;
    }

    public void clearTag(Player player) {
        equipment.clear(player);
    }

    private void sendNotEnough(Player player) {
        player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-1", "")));
        player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-2", "")));
    }
}
