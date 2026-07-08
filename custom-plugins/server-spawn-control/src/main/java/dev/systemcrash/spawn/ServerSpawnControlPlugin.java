package dev.systemcrash.spawn;

import dev.systemcrash.spawn.hub.HubMenuListener;
import dev.systemcrash.spawn.hub.HubMenuService;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSpawnControlPlugin extends JavaPlugin implements Listener {
    private static final String TARGET_WORLD = "lobby";
    private static final double SPAWN_X = -14.5D;
    private static final double SPAWN_Y = 132.0D;
    private static final double SPAWN_Z = -79.5D;
    private static final float SPAWN_YAW = 90.0F;
    private static final float SPAWN_PITCH = 0.0F;
    private static final long LOCKED_TIME = 12550L;

    private JoinWelcomeAnimation joinWelcomeAnimation;
    private LobbyProtection lobbyProtection;
    private HubMenuService hubMenuService;
    private final ConcurrentHashMap<UUID, PendingWelcome> pendingWelcomes = new ConcurrentHashMap<>();

    private static final class PendingWelcome {
        boolean spawnTeleported;
        boolean resourcePackLoaded;
        boolean animationStarted;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobbyProtection = loadLobbyProtection();
        OperatorModeSupport.setBypassPlayers(getConfig().getStringList("spawn-bypass-players"));
        joinWelcomeAnimation = new JoinWelcomeAnimation(this);
        hubMenuService = new HubMenuService(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LobbyProtectionListener(lobbyProtection), this);
        getServer().getPluginManager().registerEvents(new HubMenuListener(this, hubMenuService), this);

        // Keep world time and spawn rules in the desired state.
        getServer().getScheduler().runTaskTimer(this, this::enforceWorldState, 1L, 20L);
        scheduleJungleBiomeApply();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawn")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Location spawn = getExactSpawnLocation();
        if (spawn == null) {
            player.sendMessage("Spawn world is not loaded.");
            return true;
        }

        player.teleport(spawn);
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        pendingWelcomes.computeIfAbsent(playerId, ignored -> new PendingWelcome());
        applyLobbyRules(player);
        scheduleLobbyRulesRefresh(player);

        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            applyLobbyRules(player);
            Location spawn = getExactSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            PendingWelcome pending = pendingWelcomes.get(playerId);
            if (pending != null) {
                pending.spawnTeleported = true;
                if (player.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                    pending.resourcePackLoaded = true;
                }
                tryStartWelcomeAnimation(player, pending);
            }
        }, 5L);

        getServer().getScheduler().runTaskLater(this, () -> {
            PendingWelcome pending = pendingWelcomes.get(playerId);
            if (pending == null || pending.animationStarted) {
                return;
            }
            if (!player.isOnline()) {
                pendingWelcomes.remove(playerId);
                return;
            }
            if (!pending.spawnTeleported) {
                return;
            }
            getLogger().info("Welcome animation fallback for " + player.getName()
                    + " (resource pack status event did not complete in time).");
            pending.resourcePackLoaded = true;
            tryStartWelcomeAnimation(player, pending);
        }, 200L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applyLobbyRules(event.getPlayer());
    }

    private void applyLobbyRules(Player player) {
        if (!TARGET_WORLD.equals(player.getWorld().getName())) {
            return;
        }
        OperatorModeSupport.applyLobbyGameMode(player);
    }

    private void scheduleLobbyRulesRefresh(Player player) {
        for (long delay : new long[] {20L, 60L, 100L}) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    applyLobbyRules(player);
                }
            }, delay);
        }
    }

    private LobbyProtection loadLobbyProtection() {
        String prefix = "protection-region.";
        return new LobbyProtection(
                getConfig().getString(prefix + "world", TARGET_WORLD),
                getConfig().getInt(prefix + "min-x", -213),
                getConfig().getInt(prefix + "min-y", -64),
                getConfig().getInt(prefix + "min-z", -251),
                getConfig().getInt(prefix + "max-x", 0),
                getConfig().getInt(prefix + "max-y", 320),
                getConfig().getInt(prefix + "max-z", 0)
        );
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PendingWelcome pending = pendingWelcomes.computeIfAbsent(playerId, ignored -> new PendingWelcome());

        if (pending.animationStarted) {
            return;
        }

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                pending.resourcePackLoaded = true;
                tryStartWelcomeAnimation(player, pending);
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL -> {
                if (!pending.resourcePackLoaded) {
                    pendingWelcomes.remove(playerId);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingWelcomes.remove(playerId);
        joinWelcomeAnimation.cancelAnimation(playerId);
    }

    private void tryStartWelcomeAnimation(Player player, PendingWelcome pending) {
        if (pending.animationStarted) {
            return;
        }
        if (!pending.spawnTeleported || !pending.resourcePackLoaded) {
            return;
        }

        pending.animationStarted = true;
        pendingWelcomes.remove(player.getUniqueId());

        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                joinWelcomeAnimation.play(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location spawn = getExactSpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    private void enforceWorldState() {
        World world = getServer().getWorld(TARGET_WORLD);
        if (world == null) {
            return;
        }

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setTime(LOCKED_TIME);

        // Keep vanilla world spawn aligned with requested position and facing.
        world.setSpawnLocation((int) Math.floor(SPAWN_X), (int) SPAWN_Y, (int) Math.floor(SPAWN_Z), SPAWN_YAW);
    }

    private Location getExactSpawnLocation() {
        World world = getServer().getWorld(TARGET_WORLD);
        if (world == null) {
            return null;
        }
        return new Location(world, SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW, SPAWN_PITCH);
    }

    private void scheduleJungleBiomeApply() {
        if (getConfig().getBoolean("jungle-biome-applied", false)) {
            return;
        }

        getServer().getScheduler().runTaskLater(this, this::startJungleBiomeApply, 100L);
    }

    private void startJungleBiomeApply() {
        if (getConfig().getBoolean("jungle-biome-applied", false)) {
            return;
        }

        World world = getServer().getWorld(TARGET_WORLD);
        if (world == null) {
            getServer().getScheduler().runTaskLater(this, this::startJungleBiomeApply, 20L);
            return;
        }

        Biome jungle = Biome.JUNGLE;
        int minX = getConfig().getInt("biome-region.min-x");
        int minY = getConfig().getInt("biome-region.min-y");
        int minZ = getConfig().getInt("biome-region.min-z");
        int maxX = getConfig().getInt("biome-region.max-x");
        int maxY = getConfig().getInt("biome-region.max-y");
        int maxZ = getConfig().getInt("biome-region.max-z");

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        final int[] chunkX = {minChunkX};
        final int[] chunkZ = {minChunkZ};
        final BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = getServer().getScheduler().runTaskTimer(this, () -> {
            if (chunkX[0] > maxChunkX) {
                getConfig().set("jungle-biome-applied", true);
                saveConfig();
                getLogger().info("Jungle biome applied to spawn region without changing blocks.");
                taskHolder[0].cancel();
                return;
            }

            world.getChunkAt(chunkX[0], chunkZ[0]);

            int baseX = chunkX[0] << 4;
            int baseZ = chunkZ[0] << 4;
            for (int localX = 0; localX < 16; localX++) {
                int x = baseX + localX;
                if (x < minX || x > maxX) {
                    continue;
                }

                for (int localZ = 0; localZ < 16; localZ++) {
                    int z = baseZ + localZ;
                    if (z < minZ || z > maxZ) {
                        continue;
                    }

                    for (int y = minY; y <= maxY; y += 4) {
                        world.setBiome(x, y, z, jungle);
                    }
                }
            }

            chunkZ[0]++;
            if (chunkZ[0] > maxChunkZ) {
                chunkZ[0] = minChunkZ;
                chunkX[0]++;
            }
        }, 1L, 1L);
    }
}
