package dev.systemcrash.blade.cosmetics.killeffect;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill FX: themed particles + pack sounds.
 * OwlStudio bone ItemDisplays render as broken artifacts without ModelEngine hierarchy.
 */
public final class KillEffectPlaybackService implements Listener {
    private static final long COOLDOWN_MS = 800L;
    private static final long COLD_LOAD_DEADLINE_MS = 2_000L;

    private final BladeCosmeticsPlugin plugin;
    private final KillEffectCatalog catalog;
    private final CosmeticsRepository repository;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public KillEffectPlaybackService(
            BladeCosmeticsPlugin plugin,
            KillEffectCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        UUID killerId = killer.getUniqueId();
        Location victimLocation = victim.getLocation().add(0, 1.0, 0);
        long deathAt = System.currentTimeMillis();
        UUID worldId = victimLocation.getWorld().getUID();
        if (!repository.isKillEffectCached(killerId)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                repository.prefetchPlayer(killerId);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player currentKiller = plugin.getServer().getPlayer(killerId);
                    if (currentKiller != killer || !killer.isOnline()
                            || System.currentTimeMillis() - deathAt > COLD_LOAD_DEADLINE_MS
                            || plugin.getServer().getWorld(worldId) == null) {
                        return;
                    }
                    playKillerEffect(killer, killerId, victimLocation);
                });
            });
            return;
        }
        playKillerEffect(killer, killerId, victimLocation);
    }

    private void playKillerEffect(Player killer, UUID killerId, Location victimLocation) {
        Optional<String> equipped = repository.equippedKillEffect(killerId);
        if (equipped.isEmpty()) {
            return;
        }
        catalog.find(equipped.get()).ifPresent(effect ->
                play(killer, victimLocation, effect));
    }

    public void play(Player killer, Location at, KillEffectDefinition effect) {
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(killer.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        cooldownUntil.put(killer.getUniqueId(), now + COOLDOWN_MS);

        Location base = at.clone();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        String sound = effect.sound();
        if (sound != null && !sound.isBlank()) {
            world.playSound(base, sound, SoundCategory.PLAYERS, 1.2f, 1.0f);
        }

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick++ >= 24 || world.getPlayers().isEmpty()) {
                    cancel();
                    return;
                }
                spawnTheme(effect.id(), base, tick);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnTheme(String id, Location base, int tick) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        double t = tick / 24.0;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        switch (id) {
            case "angelic_bless" -> {
                world.spawnParticle(Particle.END_ROD, base.clone().add(0, t * 1.6, 0), 6, 0.35, 0.2, 0.35, 0.01);
                world.spawnParticle(Particle.CLOUD, base, 3, 0.4, 0.1, 0.4, 0.0);
            }
            case "arcade_gameover" -> {
                world.spawnParticle(Particle.FIREWORK, base, 8, 0.5, 0.5, 0.5, 0.05);
                world.spawnParticle(Particle.CRIT, base, 10, 0.4, 0.5, 0.4, 0.1);
            }
            case "hellfire_burn" -> {
                world.spawnParticle(Particle.FLAME, base, 14, 0.45, 0.6, 0.45, 0.02);
                world.spawnParticle(Particle.LAVA, base, 2, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.SMOKE, base, 4, 0.3, 0.4, 0.3, 0.01);
            }
            case "imposter_instinct" -> {
                world.spawnParticle(Particle.DUST, base, 12, 0.35, 0.5, 0.35, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 40, 40), 1.4f));
                world.spawnParticle(Particle.CRIT, base, 6, 0.25, 0.4, 0.25, 0.05);
            }
            case "kfx_divine_execution" -> {
                world.spawnParticle(Particle.END_ROD, base, 10, 0.2, 0.8, 0.2, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, base, 8, 0.3, 0.5, 0.3, 0.01);
            }
            case "knockout_ko" -> {
                world.spawnParticle(Particle.EXPLOSION, base, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CRIT, base, 16, 0.6, 0.6, 0.6, 0.15);
            }
            case "plantfood_feasting" -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, base, 8, 0.4, 0.5, 0.4, 0);
                world.spawnParticle(Particle.COMPOSTER, base, 6, 0.35, 0.4, 0.35, 0);
                world.spawnParticle(Particle.ITEM_SLIME, base, 4, 0.3, 0.3, 0.3, 0.02);
            }
            case "quicksand" -> {
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, -0.2 * t, 0), 12, 0.45, 0.1, 0.45, 0.05,
                        Material.SAND.createBlockData());
                world.spawnParticle(Particle.CLOUD, base, 2, 0.3, 0.05, 0.3, 0);
            }
            case "shark_attack" -> {
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, base, 10, 0.4, 0.5, 0.4, 0.02);
                world.spawnParticle(Particle.SPLASH, base, 8, 0.5, 0.2, 0.5, 0.1);
                world.spawnParticle(Particle.DUST, base, 6, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(40, 90, 140), 1.3f));
            }
            case "tentacle_grasp" -> {
                double a = tick * 0.45;
                Location ring = base.clone().add(Math.cos(a) * 0.7, 0.2, Math.sin(a) * 0.7);
                world.spawnParticle(Particle.SQUID_INK, ring, 3, 0.05, 0.1, 0.05, 0);
                world.spawnParticle(Particle.PORTAL, base, 6, 0.4, 0.5, 0.4, 0.2);
            }
            case "tertis_smash" -> {
                Color[] colors = {
                        Color.fromRGB(0, 240, 240), Color.fromRGB(240, 240, 0),
                        Color.fromRGB(160, 0, 240), Color.fromRGB(0, 240, 0)
                };
                Color c = colors[rng.nextInt(colors.length)];
                world.spawnParticle(Particle.DUST,
                        base.clone().add(rng.nextDouble(-0.5, 0.5), rng.nextDouble(0, 1.2), rng.nextDouble(-0.5, 0.5)),
                        4, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(c, 1.6f));
            }
            default -> world.spawnParticle(Particle.CRIT, base, 8, 0.4, 0.5, 0.4, 0.05);
        }
    }
}
