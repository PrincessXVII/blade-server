package dev.systemcrash.spawn;

import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ServerSpawnControlPlugin extends JavaPlugin implements Listener {
    private static final String TARGET_WORLD = "lobby";
    private static final double SPAWN_X = 3.5D;
    private static final double SPAWN_Y = 161.0D;
    private static final double SPAWN_Z = 53.5D;
    private static final float SPAWN_YAW = 90.0F;
    private static final float SPAWN_PITCH = 0.0F;
    private static final long LOCKED_TIME = 12550L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

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
        Location spawn = getExactSpawnLocation();
        if (spawn != null) {
            event.getPlayer().teleport(spawn);
        }
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
