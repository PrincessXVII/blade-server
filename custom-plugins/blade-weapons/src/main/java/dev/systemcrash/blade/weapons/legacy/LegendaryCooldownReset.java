package dev.systemcrash.blade.weapons.legacy;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

public final class LegendaryCooldownReset {
    private final LegacyDemoraBridge bridge;

    public LegendaryCooldownReset(LegacyDemoraBridge bridge) {
        this.bridge = bridge;
    }

    public void clearAll(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        bridge.getHost().cooldowns().clear(player);

        bridge.getSculkCrossbow().clearCooldowns(playerId);
        bridge.getMjolnir().clearCooldowns(playerId);
        bridge.getShadowBlade().removePlayerData(player);
        bridge.getReaperScythe().clearCooldowns(playerId);
        bridge.getVillagerStaff().clearCooldowns(playerId);
        bridge.getDragonKatana().removePlayerData(playerId);
        bridge.getExcalibur().removePlayerData(playerId);
        bridge.getVoidStaff().removePlayerData(playerId);
        bridge.getAwakenedDragonKatana().removePlayerData(playerId);
        bridge.getMagmaClub().removePlayerData(player);
        bridge.getShrinkRay().removePlayerData(player);
        bridge.getLichStaff().clearCooldowns(playerId);
        bridge.getPoseidonTrident().clearCooldowns(playerId);
        bridge.getReinforcedElytra().removePlayerData(playerId);
        bridge.getReworkElytra().removePlayerData(playerId);

        if (bridge.getChainsawSwordListener() != null) {
            bridge.getChainsawSwordListener().clearCooldowns(playerId);
        }
        if (bridge.getWitherSicklesListener() != null && bridge.getWitherSicklesListener().getCooldownManager() != null) {
            bridge.getWitherSicklesListener().getCooldownManager().removePlayerData(playerId);
        }
        if (bridge.getAwakenedWitherSicklesListener() != null
                && bridge.getAwakenedWitherSicklesListener().getCooldownManager() != null) {
            bridge.getAwakenedWitherSicklesListener().getCooldownManager().removePlayerData(playerId);
        }
        if (bridge.getCloudSwordListener() != null) {
            bridge.getCloudSwordListener().clearCooldowns(playerId);
        }
        if (bridge.getHypnosisStaffListener() != null) {
            bridge.getHypnosisStaffListener().clearCooldowns(playerId);
        }
        if (bridge.getGolemHammerListener() != null) {
            bridge.getGolemHammerListener().clearCooldowns(playerId);
        }
        if (bridge.getToxicCrossbowListener() != null) {
            bridge.getToxicCrossbowListener().clearCooldowns(playerId);
        }
        if (bridge.getFreezingChakramListener() != null) {
            bridge.getFreezingChakramListener().clearCooldowns(playerId);
        }
        if (bridge.getSculkweaversLanternListener() != null) {
            bridge.getSculkweaversLanternListener().clearCooldowns(playerId);
        }
        if (bridge.getRavagerHornListener() != null) {
            bridge.getRavagerHornListener().clearCooldowns(playerId);
        }
        if (bridge.getBloodMaceListener() != null) {
            bridge.getBloodMaceListener().clearCooldowns(playerId);
        }

        clearInventorySlotCooldowns(player);
        // Clearing item cooldowns can dump a deferred guardian hurt packet — kill only that.
        player.stopSound(Sound.ENTITY_GUARDIAN_HURT);
        for (long tick = 0L; tick <= 5L; tick++) {
            bridge.getHost().getServer().getScheduler().runTaskLater(bridge.getHost(), () -> {
                if (player.isOnline()) {
                    player.stopSound(Sound.ENTITY_GUARDIAN_HURT);
                }
            }, tick);
        }
    }

    private void clearInventorySlotCooldowns(Player player) {
        PlayerInventory inventory = player.getInventory();
        clearItemCooldown(player, inventory.getItemInMainHand());
        clearItemCooldown(player, inventory.getItemInOffHand());
        clearItemCooldown(player, inventory.getHelmet());
        clearItemCooldown(player, inventory.getChestplate());
        clearItemCooldown(player, inventory.getLeggings());
        clearItemCooldown(player, inventory.getBoots());
        for (ItemStack item : inventory.getContents()) {
            clearItemCooldown(player, item);
        }
        for (Material material : Material.values()) {
            if (material.isItem() && player.hasCooldown(material)) {
                player.setCooldown(material, 0);
            }
        }
    }

    private static void clearItemCooldown(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            player.setCooldown(item, 0);
        }
    }
}
