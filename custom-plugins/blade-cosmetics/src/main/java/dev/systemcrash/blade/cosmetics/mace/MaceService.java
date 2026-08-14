package dev.systemcrash.blade.cosmetics.mace;

import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.equip.MaceEquipmentService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class MaceService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final MaceCatalog catalog;
    private final CosmeticsRepository repository;
    private final OrbsManager orbs;
    private final MaceEquipmentService equipment;

    public MaceService(
            BladeCosmeticsPlugin plugin,
            MaceCatalog catalog,
            CosmeticsRepository repository,
            OrbsManager orbs,
            MaceEquipmentService equipment
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

    public ClickResult clickMace(Player player, String maceId) {
        var mace = catalog.find(maceId);
        if (mace.isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (repository.ownsMace(player.getUniqueId(), maceId)) {
            equipment.equip(player, maceId);
            return ClickResult.EQUIPPED;
        }
        int price = repository.macePriceOf(maceId);
        if (!orbs.trySpend(player.getUniqueId(), price)) {
            sendNotEnough(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.NOT_ENOUGH_ORBS;
        }
        repository.grantMace(player.getUniqueId(), maceId);
        equipment.equip(player, maceId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return ClickResult.PURCHASED;
    }

    public void clearMace(Player player) {
        equipment.clear(player);
    }

    private void sendNotEnough(Player player) {
        String line1 = plugin.getConfig().getString("messages.not-enough-orbs-1", "");
        String line2 = plugin.getConfig().getString("messages.not-enough-orbs-2", "");
        player.sendMessage(MM.deserialize(line1));
        player.sendMessage(MM.deserialize(line2));
    }
}
