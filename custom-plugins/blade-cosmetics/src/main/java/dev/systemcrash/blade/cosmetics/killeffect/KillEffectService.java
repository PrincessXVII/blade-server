package dev.systemcrash.blade.cosmetics.killeffect;

import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class KillEffectService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BladeCosmeticsPlugin plugin;
    private final KillEffectCatalog catalog;
    private final CosmeticsRepository repository;
    private final OrbsManager orbs;
    private final KillEffectEquipmentService equipment;

    public KillEffectService(
            BladeCosmeticsPlugin plugin,
            KillEffectCatalog catalog,
            CosmeticsRepository repository,
            OrbsManager orbs,
            KillEffectEquipmentService equipment
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

    public ClickResult clickEffect(Player player, String effectId) {
        if (catalog.find(effectId).isEmpty()) {
            return ClickResult.UNKNOWN;
        }
        if (repository.ownsKillEffect(player.getUniqueId(), effectId)) {
            equipment.equip(player, effectId);
            return ClickResult.EQUIPPED;
        }
        int price = repository.killEffectPriceOf(effectId);
        if (!orbs.trySpend(player.getUniqueId(), price)) {
            player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-1", "")));
            player.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.not-enough-orbs-2", "")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return ClickResult.NOT_ENOUGH_ORBS;
        }
        repository.grantKillEffect(player.getUniqueId(), effectId);
        equipment.equip(player, effectId);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return ClickResult.PURCHASED;
    }

    public void clearEffect(Player player) {
        equipment.clear(player);
    }
}
