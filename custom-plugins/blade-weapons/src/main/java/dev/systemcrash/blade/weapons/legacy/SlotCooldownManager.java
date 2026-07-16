package dev.systemcrash.blade.weapons.legacy;

import dev.systemcrash.blade.weapons.legacy.weapons.freezingchakram.FreezingChakramListener;
import dev.systemcrash.blade.weapons.legacy.weapons.sculkweaverslantern.SculkweaversLanternListener;
import dev.systemcrash.blade.weapons.legacy.weapons.withersickles.SickleCooldownManager;
import dev.systemcrash.blade.weapons.legacy.weapons.withersickles.rework.AwakenedSickleCooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class SlotCooldownManager {
    private final LegacyDemoraBridge bridge;
    private final Map<String, Function<Player, Long>> checkers = new LinkedHashMap<>();
    private final Map<String, CooldownTrack> notifyTracks = new LinkedHashMap<>();
    private final Set<String> activeDisplays = new HashSet<>();
    private final Map<String, Long> displayStartedSeconds = new HashMap<>();
    private final Map<String, Long> lastNotifyRemaining = new HashMap<>();

    private record CooldownTrack(String weaponId, Function<Player, Long> remainingSeconds) {
    }

    public SlotCooldownManager(LegacyDemoraBridge bridge) {
        this.bridge = bridge;
        wireCheckers();
        wireNotifyTracks();
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    applySlotCooldown(player);
                    trackAbilityCooldownReady(player);
                }
            }
        }.runTaskTimer(bridge.getHost(), 0L, 5L);
    }

    public void startDisplay(Player player, ItemStack item, int durationSeconds) {
        if (player == null || item == null || item.getType() == Material.AIR || durationSeconds <= 0) {
            return;
        }

        String weaponId = identifyWeapon(item);
        if (weaponId == null || "villager_staff".equals(weaponId)) {
            return;
        }

        int slot = findItemSlot(player, item);
        if (slot < 0) {
            return;
        }
        String key = displayKey(player.getUniqueId(), slot, weaponId);
        int ticks = (int) Math.min(Integer.MAX_VALUE, durationSeconds * 20L);
        player.setCooldown(item, ticks);
        activeDisplays.add(key);
        displayStartedSeconds.put(key, (long) durationSeconds);
    }

    private void wireCheckers() {
        checkers.put("sculk_crossbow", p -> bridge.getSculkCrossbow().isOnCooldown(p) ? bridge.getSculkCrossbow().getRemainingCooldown(p) : 0L);
        checkers.put("mjolnir", p -> {
            long throwCd = bridge.getMjolnir().isOnThrowCooldown(p) ? bridge.getMjolnir().getRemainingThrowCooldown(p) : 0L;
            long meleeCd = bridge.getMjolnir().isOnMeleeCooldown(p) ? bridge.getMjolnir().getRemainingMeleeCooldown(p) : 0L;
            return Math.max(throwCd, meleeCd);
        });
        checkers.put("shadow_blade", p -> bridge.getShadowBlade().isOnCooldown(p) ? bridge.getShadowBlade().getRemainingCooldown(p) : 0L);
        checkers.put("reaper_scythe", p -> bridge.getReaperScythe().isOnCooldown(p) ? bridge.getReaperScythe().getRemainingCooldown(p) : 0L);
        checkers.put("villager_staff", p -> bridge.getVillagerStaff().isOnCooldown(p) ? bridge.getVillagerStaff().getRemainingCooldown(p) : 0L);
        checkers.put("dragon_katana", p -> bridge.getDragonKatana().isOnCooldown(p) ? bridge.getDragonKatana().getRemainingCooldown(p) : 0L);
        checkers.put("awakened_dragon_katana", p -> bridge.getAwakenedDragonKatana().isOnCooldown(p) ? bridge.getAwakenedDragonKatana().getRemainingCooldown(p) : 0L);
        checkers.put("excalibur", p -> bridge.getExcalibur().isOnCooldown(p) ? bridge.getExcalibur().getRemainingCooldown(p) : 0L);
        checkers.put("void_staff", p -> bridge.getVoidStaff().isOnPortalCooldown(p) ? bridge.getVoidStaff().getRemainingPortalCooldown(p) : 0L);
        checkers.put("magma_club", p -> bridge.getMagmaClub().isOnCooldown(p) ? bridge.getMagmaClub().getRemainingCooldown(p) : 0L);
        checkers.put("shrink_ray", p -> bridge.getShrinkRay().isOnCooldown(p) ? bridge.getShrinkRay().getRemainingCooldown(p) : 0L);
        checkers.put("chainsaw_sword", p -> {
            if (bridge.getChainsawSwordListener() != null && bridge.getChainsawSwordListener().isOnCurseCooldown(p)) {
                return bridge.getChainsawSwordListener().getRemainingCurseCooldown(p);
            }
            return 0L;
        });
        checkers.put("wither_sickles", p -> {
            if (bridge.getWitherSicklesListener() != null) {
                SickleCooldownManager manager = bridge.getWitherSicklesListener().getCooldownManager();
                if (manager != null && manager.isOnCooldown(p)) {
                    return manager.getRemainingCooldown(p);
                }
            }
            return 0L;
        });
        checkers.put("awakened_wither_sickles", p -> {
            if (bridge.getAwakenedWitherSicklesListener() != null) {
                AwakenedSickleCooldownManager manager = bridge.getAwakenedWitherSicklesListener().getCooldownManager();
                if (manager != null && manager.isOnCooldown(p)) {
                    return manager.getRemainingCooldown(p);
                }
            }
            return 0L;
        });
        checkers.put("cloud_sword", p -> bridge.getCloudSwordListener() != null && bridge.getCloudSwordListener().isOnCooldown(p)
                ? bridge.getCloudSwordListener().getRemainingCooldown(p) : 0L);
        checkers.put("hypnosis_staff", p -> bridge.getHypnosisStaffListener() != null && bridge.getHypnosisStaffListener().isOnCooldown(p)
                ? bridge.getHypnosisStaffListener().getRemainingCooldown(p) : 0L);
        checkers.put("golem_hammer", p -> bridge.getGolemHammerListener() != null && bridge.getGolemHammerListener().isOnCooldown(p)
                ? bridge.getGolemHammerListener().getRemainingCooldown(p) : 0L);
        checkers.put("toxic_crossbow", p -> bridge.getToxicCrossbowListener() != null && bridge.getToxicCrossbowListener().isOnCooldown(p)
                ? bridge.getToxicCrossbowListener().getRemainingCooldown(p) : 0L);
        checkers.put("lich_staff", p -> bridge.getLichStaff().isOnCooldown(p) ? bridge.getLichStaff().getRemainingCooldown(p) : 0L);
        checkers.put("poseidon_trident", p -> Math.max(
                bridge.getPoseidonTrident().getRemainingThrowCooldown(p.getUniqueId()),
                bridge.getPoseidonTrident().getRemainingRiptideCooldown(p.getUniqueId())
        ));
        checkers.put("reinforced_elytra", p -> {
            long boost = bridge.getReinforcedElytra().isOnBoostCooldown(p.getUniqueId())
                    ? bridge.getReinforcedElytra().getRemainingBoostCooldownSeconds(p.getUniqueId()) : 0L;
            long landing = bridge.getReinforcedElytra().getRemainingExplosionLandingCooldownSeconds(p.getUniqueId());
            return Math.max(boost, landing);
        });
        checkers.put("rework_elytra", p -> {
            long boost = bridge.getReworkElytra().isOnBoostCooldown(p.getUniqueId())
                    ? bridge.getReworkElytra().getRemainingBoostCooldownSeconds(p.getUniqueId()) : 0L;
            long landing = bridge.getReworkElytra().getRemainingExplosionLandingCooldownSeconds(p.getUniqueId());
            return Math.max(boost, landing);
        });
        checkers.put("ravager_horn", p -> bridge.getRavagerHornListener() == null ? 0L
                : bridge.getRavagerHornListener().getRemainingCooldownSeconds(p.getUniqueId()));
        checkers.put("freezing_chakram", p -> {
            FreezingChakramListener listener = bridge.getFreezingChakramListener();
            return listener != null ? listener.getRemainingCooldownSeconds(p.getUniqueId()) : 0L;
        });
        checkers.put("sculkweavers_lantern", p -> {
            SculkweaversLanternListener listener = bridge.getSculkweaversLanternListener();
            return listener != null ? listener.getRemainingCooldownSeconds(p.getUniqueId()) : 0L;
        });
        checkers.put("blood_mace", p -> bridge.getBloodMace().isOnCooldown(p.getUniqueId())
                ? bridge.getBloodMace().remainingCooldownSeconds(p.getUniqueId()) : 0L);
    }

    private void wireNotifyTracks() {
        notifyTracks.put("sculk_crossbow", track("sculk_crossbow", p -> bridge.getSculkCrossbow().isOnCooldown(p) ? bridge.getSculkCrossbow().getRemainingCooldown(p) : 0L));
        notifyTracks.put("mjolnir_throw", track("mjolnir", p -> bridge.getMjolnir().isOnThrowCooldown(p) ? bridge.getMjolnir().getRemainingThrowCooldown(p) : 0L));
        notifyTracks.put("mjolnir_melee", track("mjolnir", p -> bridge.getMjolnir().isOnMeleeCooldown(p) ? bridge.getMjolnir().getRemainingMeleeCooldown(p) : 0L));
        notifyTracks.put("shadow_blade", track("shadow_blade", p -> bridge.getShadowBlade().isOnCooldown(p) ? bridge.getShadowBlade().getRemainingCooldown(p) : 0L));
        notifyTracks.put("reaper_scythe", track("reaper_scythe", p -> bridge.getReaperScythe().isOnCooldown(p) ? bridge.getReaperScythe().getRemainingCooldown(p) : 0L));
        notifyTracks.put("villager_staff", track("villager_staff", p -> bridge.getVillagerStaff().isOnCooldown(p) ? bridge.getVillagerStaff().getRemainingCooldown(p) : 0L));
        notifyTracks.put("dragon_katana", track("dragon_katana", p -> bridge.getDragonKatana().isOnCooldown(p) ? bridge.getDragonKatana().getRemainingCooldown(p) : 0L));
        notifyTracks.put("awakened_dragon_katana", track("awakened_dragon_katana", p -> bridge.getAwakenedDragonKatana().isOnCooldown(p) ? bridge.getAwakenedDragonKatana().getRemainingCooldown(p) : 0L));
        notifyTracks.put("excalibur", track("excalibur", p -> bridge.getExcalibur().isOnCooldown(p) ? bridge.getExcalibur().getRemainingCooldown(p) : 0L));
        notifyTracks.put("void_staff", track("void_staff", p -> bridge.getVoidStaff().isOnPortalCooldown(p) ? bridge.getVoidStaff().getRemainingPortalCooldown(p) : 0L));
        notifyTracks.put("magma_club", track("magma_club", p -> bridge.getMagmaClub().isOnCooldown(p) ? bridge.getMagmaClub().getRemainingCooldown(p) : 0L));
        notifyTracks.put("shrink_ray", track("shrink_ray", p -> bridge.getShrinkRay().isOnCooldown(p) ? bridge.getShrinkRay().getRemainingCooldown(p) : 0L));
        notifyTracks.put("chainsaw_sword", track("chainsaw_sword", p -> {
            if (bridge.getChainsawSwordListener() != null && bridge.getChainsawSwordListener().isOnCurseCooldown(p)) {
                return bridge.getChainsawSwordListener().getRemainingCurseCooldown(p);
            }
            return 0L;
        }));
        notifyTracks.put("wither_sickles", track("wither_sickles", p -> {
            if (bridge.getWitherSicklesListener() != null) {
                SickleCooldownManager manager = bridge.getWitherSicklesListener().getCooldownManager();
                if (manager != null && manager.isOnCooldown(p)) {
                    return manager.getRemainingCooldown(p);
                }
            }
            return 0L;
        }));
        notifyTracks.put("awakened_wither_sickles", track("awakened_wither_sickles", p -> {
            if (bridge.getAwakenedWitherSicklesListener() != null) {
                AwakenedSickleCooldownManager manager = bridge.getAwakenedWitherSicklesListener().getCooldownManager();
                if (manager != null && manager.isOnCooldown(p)) {
                    return manager.getRemainingCooldown(p);
                }
            }
            return 0L;
        }));
        notifyTracks.put("cloud_sword", track("cloud_sword", p -> bridge.getCloudSwordListener() != null && bridge.getCloudSwordListener().isOnCooldown(p)
                ? bridge.getCloudSwordListener().getRemainingCooldown(p) : 0L));
        notifyTracks.put("hypnosis_staff", track("hypnosis_staff", p -> bridge.getHypnosisStaffListener() != null && bridge.getHypnosisStaffListener().isOnCooldown(p)
                ? bridge.getHypnosisStaffListener().getRemainingCooldown(p) : 0L));
        notifyTracks.put("golem_hammer", track("golem_hammer", p -> bridge.getGolemHammerListener() != null && bridge.getGolemHammerListener().isOnCooldown(p)
                ? bridge.getGolemHammerListener().getRemainingCooldown(p) : 0L));
        notifyTracks.put("toxic_crossbow", track("toxic_crossbow", p -> bridge.getToxicCrossbowListener() != null && bridge.getToxicCrossbowListener().isOnCooldown(p)
                ? bridge.getToxicCrossbowListener().getRemainingCooldown(p) : 0L));
        notifyTracks.put("lich_staff", track("lich_staff", p -> bridge.getLichStaff().isOnCooldown(p) ? bridge.getLichStaff().getRemainingCooldown(p) : 0L));
        notifyTracks.put("poseidon_throw", track("poseidon_trident", p -> bridge.getPoseidonTrident().isOnThrowCooldown(p.getUniqueId())
                ? bridge.getPoseidonTrident().getRemainingThrowCooldown(p.getUniqueId()) : 0L));
        notifyTracks.put("poseidon_riptide", track("poseidon_trident", p -> bridge.getPoseidonTrident().isOnRiptideCooldown(p.getUniqueId())
                ? bridge.getPoseidonTrident().getRemainingRiptideCooldown(p.getUniqueId()) : 0L));
        notifyTracks.put("reinforced_elytra_boost", track("reinforced_elytra", p -> bridge.getReinforcedElytra().isOnBoostCooldown(p.getUniqueId())
                ? bridge.getReinforcedElytra().getRemainingBoostCooldownSeconds(p.getUniqueId()) : 0L));
        notifyTracks.put("reinforced_elytra_landing", track("reinforced_elytra", p -> {
            long landing = bridge.getReinforcedElytra().getRemainingExplosionLandingCooldownSeconds(p.getUniqueId());
            return landing > 0L ? landing : 0L;
        }));
        notifyTracks.put("rework_elytra_boost", track("rework_elytra", p -> bridge.getReworkElytra().isOnBoostCooldown(p.getUniqueId())
                ? bridge.getReworkElytra().getRemainingBoostCooldownSeconds(p.getUniqueId()) : 0L));
        notifyTracks.put("rework_elytra_landing", track("rework_elytra", p -> {
            long landing = bridge.getReworkElytra().getRemainingExplosionLandingCooldownSeconds(p.getUniqueId());
            return landing > 0L ? landing : 0L;
        }));
        notifyTracks.put("ravager_horn", track("ravager_horn", p -> bridge.getRavagerHornListener() == null ? 0L
                : bridge.getRavagerHornListener().getRemainingCooldownSeconds(p.getUniqueId())));
        notifyTracks.put("freezing_chakram", track("freezing_chakram", p -> {
            FreezingChakramListener listener = bridge.getFreezingChakramListener();
            return listener != null ? listener.getRemainingCooldownSeconds(p.getUniqueId()) : 0L;
        }));
        notifyTracks.put("sculkweavers_lantern", track("sculkweavers_lantern", p -> {
            SculkweaversLanternListener listener = bridge.getSculkweaversLanternListener();
            return listener != null ? listener.getRemainingCooldownSeconds(p.getUniqueId()) : 0L;
        }));
        notifyTracks.put("blood_mace", track("blood_mace", p -> bridge.getBloodMace().isOnCooldown(p.getUniqueId())
                ? bridge.getBloodMace().remainingCooldownSeconds(p.getUniqueId()) : 0L));
    }

    private static CooldownTrack track(String weaponId, Function<Player, Long> remainingSeconds) {
        return new CooldownTrack(weaponId, remainingSeconds);
    }

    private void trackAbilityCooldownReady(Player player) {
        UUID playerId = player.getUniqueId();

        for (Map.Entry<String, CooldownTrack> entry : notifyTracks.entrySet()) {
            String trackKey = playerId + ":" + entry.getKey();
            long remaining = entry.getValue().remainingSeconds().apply(player);
            Long previous = lastNotifyRemaining.get(trackKey);

            if (remaining <= 0L) {
                if (previous != null && previous > 0L) {
                    WeaponCooldownNotifier.notifyReady(player, bridge, entry.getValue().weaponId());
                }
                lastNotifyRemaining.remove(trackKey);
            } else {
                lastNotifyRemaining.put(trackKey, remaining);
            }
        }
    }

    private void applySlotCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> seenKeys = new HashSet<>();

        for (int slot = 0; slot < 36; slot++) {
            seenKeys.add(applyItemCooldown(player, player.getInventory().getItem(slot), slot));
        }
        seenKeys.add(applyItemCooldown(player, player.getInventory().getItemInOffHand(), 40));
        seenKeys.add(applyItemCooldown(player, player.getInventory().getChestplate(), 38));
        seenKeys.add(applyItemCooldown(player, player.getInventory().getLeggings(), 37));
        seenKeys.add(applyItemCooldown(player, player.getInventory().getBoots(), 36));
        seenKeys.add(applyItemCooldown(player, player.getInventory().getHelmet(), 39));

        activeDisplays.removeIf(key -> {
            if (!key.startsWith(playerId + ":")) {
                return false;
            }
            if (seenKeys.contains(key)) {
                return false;
            }
            displayStartedSeconds.remove(key);
            return true;
        });
    }

    private String applyItemCooldown(Player player, ItemStack item, int slot) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        String weaponId = identifyWeapon(item);
        if (weaponId == null) {
            return null;
        }

        Function<Player, Long> checker = checkers.get(weaponId);
        if (checker == null) {
            return null;
        }

        String key = displayKey(player.getUniqueId(), slot, weaponId);
        long remainingSeconds = checker.apply(player);

        if (remainingSeconds <= 0L) {
            if (activeDisplays.remove(key)) {
                player.setCooldown(item, 0);
            }
            displayStartedSeconds.remove(key);
            return key;
        }

        // No item-cooldown overlay for villager staff (ability CD is tracked in memory only).
        if ("villager_staff".equals(weaponId)) {
            activeDisplays.remove(key);
            displayStartedSeconds.remove(key);
            return key;
        }

        Long startedSeconds = displayStartedSeconds.get(key);
        boolean needsRefresh = !activeDisplays.contains(key)
            || startedSeconds == null
            || remainingSeconds > startedSeconds + 1L;

        if (needsRefresh) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, remainingSeconds * 20L);
            player.setCooldown(item, ticks);
            activeDisplays.add(key);
            displayStartedSeconds.put(key, remainingSeconds);
        }

        return key;
    }

    private static String displayKey(UUID playerId, int slot, String weaponId) {
        return playerId + ":" + slot + ":" + weaponId;
    }

    private static int findItemSlot(Player player, ItemStack item) {
        if (item.equals(player.getInventory().getItemInOffHand())) {
            return 40;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (item.equals(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private String identifyWeapon(ItemStack item) {
        for (String weaponId : checkers.keySet()) {
            if (isWeapon(item, weaponId)) {
                return weaponId;
            }
        }
        return null;
    }

    private boolean isWeapon(ItemStack item, String weaponId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return switch (weaponId) {
            case "sculk_crossbow" -> bridge.getSculkCrossbow().isSculkCrossbow(item);
            case "toxic_crossbow" -> bridge.getToxicCrossbow().isToxicCrossbow(item);
            case "shrink_ray" -> bridge.getShrinkRay().isShrinkRay(item);
            case "midas_sword" -> bridge.getMidasSword().isMidasSword(item);
            case "emerald_blade" -> bridge.getEmeraldBlade().isEmeraldBlade(item);
            case "shadow_blade" -> bridge.getShadowBlade().isShadowBlade(item);
            case "magma_club" -> bridge.getMagmaClub().isMagmaClub(item);
            case "cloud_sword" -> bridge.getCloudSword().isCloudSword(item);
            case "chainsaw_sword" -> bridge.getChainsawSword().isChainsawSword(item);
            case "freezing_chakram" -> bridge.getFreezingChakram().isFreezingChakram(item);
            case "dragon_katana" -> bridge.getDragonKatana().isDragonKatana(item);
            case "excalibur" -> bridge.getExcalibur().isExcalibur(item);
            case "awakened_dragon_katana" -> bridge.getAwakenedDragonKatana().isAwakenedDragonKatana(item);
            case "reaper_scythe" -> bridge.getReaperScythe().isReaperScythe(item);
            case "wither_sickles" -> bridge.getWitherSickles().isWitherSickle(item);
            case "awakened_wither_sickles" -> bridge.getAwakenedWitherSickles().isAwakenedWitherSickle(item);
            case "mjolnir" -> bridge.getMjolnir().isMjolnir(item);
            case "golem_hammer" -> bridge.getGolemHammer().isGolemHammer(item);
            case "villager_staff" -> bridge.getVillagerStaff().isVillagerStaff(item);
            case "void_staff" -> bridge.getVoidStaff().isVoidStaff(item);
            case "hypnosis_staff" -> bridge.getHypnosisStaff().isHypnosisStaff(item);
            case "lich_staff" -> bridge.getLichStaff().isLichStaff(item);
            case "poseidon_trident" -> bridge.getPoseidonTrident().isPoseidonTrident(item);
            case "reinforced_elytra" -> bridge.getReinforcedElytra().isReinforcedElytra(item);
            case "rework_elytra" -> bridge.getReworkElytra().isReworkElytra(item);
            case "ravager_horn" -> bridge.getRavagerHorn().isRavagerHorn(item);
            case "sculkweavers_lantern" -> bridge.getSculkweaversLantern().isSculkweaversLantern(item);
            case "blood_mace" -> bridge.getBloodMace().isBloodMace(item);
            case "ballista" -> bridge.getBallista().isBallista(item);
            default -> false;
        };
    }
}
