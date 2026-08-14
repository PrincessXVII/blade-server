package dev.systemcrash.blade.cosmetics.data;

import dev.systemcrash.blade.cosmetics.hat.HatCatalog;
import dev.systemcrash.blade.cosmetics.hat.HatDefinition;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectCatalog;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectDefinition;
import dev.systemcrash.blade.cosmetics.mace.MaceCatalog;
import dev.systemcrash.blade.cosmetics.mace.MaceDefinition;
import dev.systemcrash.blade.cosmetics.sword.SwordCatalog;
import dev.systemcrash.blade.cosmetics.sword.SwordDefinition;
import dev.systemcrash.blade.cosmetics.tag.TagCatalog;
import dev.systemcrash.blade.cosmetics.tag.TagDefinition;
import dev.systemcrash.blade.cosmetics.title.TitleCatalog;
import dev.systemcrash.blade.cosmetics.title.TitleDefinition;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorCatalog;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticsRepository {
    private final JavaPlugin plugin;
    private Connection sqliteConnection;
    private Object networkDb;
    private boolean mysql;
    private boolean externalConnection;

    private final Map<String, Integer> hatPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> swordPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> macePrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> titlePrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> titleColorPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> killEffectPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> tagPrices = new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> ownedHatsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedSwordsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedMacesCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedTitlesCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedTitleColorsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedKillEffectsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> ownedTagsCache = new ConcurrentHashMap<>();

    private final Map<UUID, Optional<String>> equippedHatCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedSwordCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedMaceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedTitleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedTitleColorCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedKillEffectCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedTagCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedCageCache = new ConcurrentHashMap<>();

    public CosmeticsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String sql(String sqlite) {
        if (!mysql) {
            return sqlite;
        }
        String s = sqlite.replace("INSERT OR IGNORE", "INSERT IGNORE");
        s = s.replaceAll(
                "ON CONFLICT\\([^)]+\\) DO UPDATE SET (\\w+)\\s*=\\s*excluded\\.\\1",
                "ON DUPLICATE KEY UPDATE $1 = VALUES($1)");
        return s;
    }

    private Connection conn() throws SQLException {
        if (externalConnection && networkDb != null) {
            try {
                return (Connection) networkDb.getClass().getMethod("connection").invoke(networkDb);
            } catch (ReflectiveOperationException ex) {
                throw new SQLException("BladeCore network DB unavailable", ex);
            }
        }
        if (sqliteConnection == null || sqliteConnection.isClosed()) {
            throw new SQLException("Cosmetics SQLite connection closed");
        }
        return sqliteConnection;
    }

    public boolean usingMysql() {
        return mysql;
    }

    /**
     * Load player cosmetics into temp maps, then swap into cache atomically.
     * Never invalidate mid-flight (avoids sync DB on main-thread apply during join).
     */
    public void prefetchPlayer(UUID uuid) {
        Set<String> hats = loadHatOwned(uuid);
        Set<String> swords = loadSwordOwned(uuid);
        Set<String> maces = loadMaceOwned(uuid);
        Set<String> titles = loadTitleOwned(uuid);
        Set<String> titleColors = loadTitleColorOwned(uuid);
        Set<String> killEffects = loadKillEffectOwned(uuid);
        Set<String> tags = loadTagOwned(uuid);
        Optional<String> eqHat = loadHatEquipped(uuid);
        Optional<String> eqSword = loadSwordEquipped(uuid);
        Optional<String> eqMace = loadMaceEquipped(uuid);
        Optional<String> eqTitle = loadTitleEquipped(uuid);
        Optional<String> eqTitleColor = loadTitleColorEquipped(uuid);
        Optional<String> eqKill = loadKillEffectEquipped(uuid);
        Optional<String> eqTag = loadTagEquipped(uuid);
        Optional<String> eqCage = loadCageEquipped(uuid);

        ownedHatsCache.put(uuid, hats);
        ownedSwordsCache.put(uuid, swords);
        ownedMacesCache.put(uuid, maces);
        ownedTitlesCache.put(uuid, titles);
        ownedTitleColorsCache.put(uuid, titleColors);
        ownedKillEffectsCache.put(uuid, killEffects);
        ownedTagsCache.put(uuid, tags);
        equippedHatCache.put(uuid, eqHat);
        equippedSwordCache.put(uuid, eqSword);
        equippedMaceCache.put(uuid, eqMace);
        equippedTitleCache.put(uuid, eqTitle);
        equippedTitleColorCache.put(uuid, eqTitleColor);
        equippedKillEffectCache.put(uuid, eqKill);
        equippedTagCache.put(uuid, eqTag);
        equippedCageCache.put(uuid, eqCage);
    }


    private final Set<UUID> prefetchInflight = ConcurrentHashMap.newKeySet();

    /** Never touch JDBC on the main thread — warm async if cache cold. */
    private void ensurePlayerCached(UUID uuid) {
        if (ownedHatsCache.containsKey(uuid)
                && ownedSwordsCache.containsKey(uuid)
                && ownedMacesCache.containsKey(uuid)
                && ownedTitlesCache.containsKey(uuid)
                && ownedTitleColorsCache.containsKey(uuid)
                && ownedKillEffectsCache.containsKey(uuid)
                && ownedTagsCache.containsKey(uuid)
                && equippedHatCache.containsKey(uuid)
                && equippedSwordCache.containsKey(uuid)
                && equippedMaceCache.containsKey(uuid)
                && equippedTitleCache.containsKey(uuid)
                && equippedTitleColorCache.containsKey(uuid)
                && equippedKillEffectCache.containsKey(uuid)
                && equippedTagCache.containsKey(uuid)
                && equippedCageCache.containsKey(uuid)) {
            return;
        }
        if (!prefetchInflight.add(uuid)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ownedHatsCache.containsKey(uuid)) {
                    ownedHatsCache.put(uuid, loadHatOwned(uuid));
                }
                if (!ownedSwordsCache.containsKey(uuid)) {
                    ownedSwordsCache.put(uuid, loadSwordOwned(uuid));
                }
                if (!ownedMacesCache.containsKey(uuid)) {
                    ownedMacesCache.put(uuid, loadMaceOwned(uuid));
                }
                if (!ownedTitlesCache.containsKey(uuid)) {
                    ownedTitlesCache.put(uuid, loadTitleOwned(uuid));
                }
                if (!ownedTitleColorsCache.containsKey(uuid)) {
                    ownedTitleColorsCache.put(uuid, loadTitleColorOwned(uuid));
                }
                if (!ownedKillEffectsCache.containsKey(uuid)) {
                    ownedKillEffectsCache.put(uuid, loadKillEffectOwned(uuid));
                }
                if (!ownedTagsCache.containsKey(uuid)) {
                    ownedTagsCache.put(uuid, loadTagOwned(uuid));
                }
                equippedHatCache.putIfAbsent(uuid, loadHatEquipped(uuid));
                equippedSwordCache.putIfAbsent(uuid, loadSwordEquipped(uuid));
                equippedMaceCache.putIfAbsent(uuid, loadMaceEquipped(uuid));
                equippedTitleCache.putIfAbsent(uuid, loadTitleEquipped(uuid));
                equippedTitleColorCache.putIfAbsent(uuid, loadTitleColorEquipped(uuid));
                equippedKillEffectCache.putIfAbsent(uuid, loadKillEffectEquipped(uuid));
                equippedTagCache.putIfAbsent(uuid, loadTagEquipped(uuid));
                equippedCageCache.putIfAbsent(uuid, loadCageEquipped(uuid));
            } finally {
                prefetchInflight.remove(uuid);
            }
        });
    }

    @FunctionalInterface
    private interface SqlWrite {
        void run() throws SQLException;
    }

    private void writeAsync(String label, SqlWrite write) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write.run();
            } catch (SQLException ex) {
                plugin.getLogger().warning(label + " failed: " + ex.getMessage());
            }
        });
    }

    /** Drop in-memory state so the next read comes from the shared database. */
    public void invalidatePlayer(UUID uuid) {
        ownedHatsCache.remove(uuid);
        ownedSwordsCache.remove(uuid);
        ownedMacesCache.remove(uuid);
        ownedTitlesCache.remove(uuid);
        ownedTitleColorsCache.remove(uuid);
        ownedKillEffectsCache.remove(uuid);
        ownedTagsCache.remove(uuid);
        equippedHatCache.remove(uuid);
        equippedSwordCache.remove(uuid);
        equippedMaceCache.remove(uuid);
        equippedTitleCache.remove(uuid);
        equippedTitleColorCache.remove(uuid);
        equippedKillEffectCache.remove(uuid);
        equippedTagCache.remove(uuid);
        equippedCageCache.remove(uuid);
    }

    public void open() {
        try {
            org.bukkit.plugin.Plugin corePlugin = plugin.getServer().getPluginManager().getPlugin("BladeCore");
            boolean coreWantsMysql = false;
            if (corePlugin != null && corePlugin.isEnabled()) {
                try {
                    var cfg = ((org.bukkit.plugin.java.JavaPlugin) corePlugin).getConfig();
                    coreWantsMysql = cfg.getBoolean("database.enabled", false);
                } catch (Exception ignored) {
                }
                try {
                    Object netDb = corePlugin.getClass().getMethod("getNetworkDatabase").invoke(corePlugin);
                    if (netDb != null) {
                        Boolean enabled = (Boolean) netDb.getClass().getMethod("enabled").invoke(netDb);
                        if (Boolean.TRUE.equals(enabled)) {
                            netDb.getClass().getMethod("connection").invoke(netDb);
                            networkDb = netDb;
                            mysql = true;
                            externalConnection = true;
                            plugin.getLogger().info("Cosmetics using shared MySQL (BladeCore) — sync on");
                            ensureMaceTables();
                            return;
                        }
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("BladeCore network DB lookup failed: " + ex.getMessage());
                }
            }
            if (coreWantsMysql) {
                plugin.getLogger().severe(
                        "BladeCore database.enabled=true but NetworkDatabase is null. "
                                + "Cosmetics sync DISABLED until MySQL works. Check BladeCore log for SSL/IP errors.");
                // Still open SQLite as temporary emergency so the plugin loads — but flag clearly.
            }
            File db = new File(plugin.getDataFolder(), "cosmetics.db");
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Cannot create plugin data folder");
            }
            Class.forName("org.sqlite.JDBC");
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            mysql = false;
            externalConnection = false;
            plugin.getLogger().warning("Cosmetics on local SQLite — network sync DISABLED");
            try (Statement st = sqliteConnection.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS hat_prices (
                          hat_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_hats (
                          uuid TEXT NOT NULL,
                          hat_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, hat_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_state (
                          uuid TEXT PRIMARY KEY,
                          equipped_hat_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS sword_prices (
                          sword_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_swords (
                          uuid TEXT NOT NULL,
                          sword_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, sword_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_sword (
                          uuid TEXT PRIMARY KEY,
                          sword_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS title_prices (
                          title_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_titles (
                          uuid TEXT NOT NULL,
                          title_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, title_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_title (
                          uuid TEXT PRIMARY KEY,
                          title_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS title_color_prices (
                          color_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_title_colors (
                          uuid TEXT NOT NULL,
                          color_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, color_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_title_color (
                          uuid TEXT PRIMARY KEY,
                          color_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS kill_effect_prices (
                          effect_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_kill_effects (
                          uuid TEXT NOT NULL,
                          effect_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, effect_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_kill_effect (
                          uuid TEXT PRIMARY KEY,
                          effect_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS tag_prices (
                          tag_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_tags (
                          uuid TEXT NOT NULL,
                          tag_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, tag_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_tag (
                          uuid TEXT PRIMARY KEY,
                          tag_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_cage (
                          uuid TEXT PRIMARY KEY,
                          cage_id TEXT
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mace_prices (
                          mace_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_maces (
                          uuid TEXT NOT NULL,
                          mace_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, mace_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_mace (
                          uuid TEXT PRIMARY KEY,
                          mace_id TEXT
                        )
                        """);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to open cosmetics DB", ex);
        }
    }

    private void ensureMaceTables() {
        try (Statement st = conn().createStatement()) {
            if (mysql) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mace_prices (
                          mace_id VARCHAR(64) PRIMARY KEY,
                          price INT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_maces (
                          uuid VARCHAR(36) NOT NULL,
                          mace_id VARCHAR(64) NOT NULL,
                          purchased_at BIGINT NOT NULL,
                          PRIMARY KEY (uuid, mace_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_mace (
                          uuid VARCHAR(36) PRIMARY KEY,
                          mace_id VARCHAR(64)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } else {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mace_prices (
                          mace_id TEXT PRIMARY KEY,
                          price INTEGER NOT NULL
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS owned_maces (
                          uuid TEXT NOT NULL,
                          mace_id TEXT NOT NULL,
                          purchased_at INTEGER NOT NULL,
                          PRIMARY KEY (uuid, mace_id)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS equipped_mace (
                          uuid TEXT PRIMARY KEY,
                          mace_id TEXT
                        )
                        """);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create mace tables", ex);
        }
    }

    private void upsertPrices(
            String table,
            String idColumn,
            List<String> ids,
            List<Integer> pool,
            Map<String, Integer> cache,
            String label
    ) {
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("price pool is empty");
        }
        Map<String, Integer> assigned = PriceLadder.assign(ids, pool);
        try {
            Connection cnx = conn();
            try (PreparedStatement ps = cnx.prepareStatement(sql(
                    "INSERT INTO " + table + "(" + idColumn + ", price) VALUES(?, ?) "
                            + "ON CONFLICT(" + idColumn + ") DO UPDATE SET price = excluded.price"))) {
                int pending = 0;
                for (var e : assigned.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setInt(2, e.getValue());
                    ps.addBatch();
                    pending++;
                    if (pending % 100 == 0) {
                        ps.executeBatch();
                    }
                }
                if (pending > 0) {
                    ps.executeBatch();
                }
            }
            cache.clear();
            cache.putAll(assigned);
            plugin.getLogger().info("Cached " + label + " prices: " + cache.size());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to seed " + label + " prices", ex);
        }
    }

    public void close() {
        networkDb = null;
        if (externalConnection) {
            return;
        }
        try {
            if (sqliteConnection != null) {
                sqliteConnection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    public void seedHatPrices(HatCatalog catalog, List<Integer> pool) {
        upsertPrices("hat_prices", "hat_id",
                catalog.all().stream().map(HatDefinition::id).toList(), pool, hatPrices, "Hat");
    }

    public int priceOf(String hatId) {
        return hatPrices.getOrDefault(hatId, 100);
    }

    public boolean ownsHat(UUID uuid, String hatId) {
        return ownedHats(uuid).contains(hatId);
    }

    public Set<String> ownedHats(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedHatsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadHatOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT hat_id FROM owned_hats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedHats failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantHat(UUID uuid, String hatId) {
        ownedHatsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(hatId);
        writeAsync("grantHat", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_hats(uuid, hat_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, hatId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    /** True once equipped-hat row has been loaded into memory (even if empty). */
    public boolean isHatStateCached(UUID uuid) {
        return equippedHatCache.containsKey(uuid);
    }

    public Optional<String> equippedHat(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedHatCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadHatEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT equipped_hat_id FROM player_state WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedHat failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedHat(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedHatCache.put(uuid, Optional.empty());
        } else {
            equippedHatCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedHat", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO player_state(uuid, equipped_hat_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET equipped_hat_id = excluded.equipped_hat_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedSwordPrices(SwordCatalog catalog, List<Integer> pool) {
        upsertPrices("sword_prices", "sword_id",
                catalog.all().stream().map(SwordDefinition::id).toList(), pool, swordPrices, "Sword");
    }

    public int swordPriceOf(String swordId) {
        return swordPrices.getOrDefault(swordId, 100);
    }

    public boolean ownsSword(UUID uuid, String swordId) {
        return ownedSwords(uuid).contains(swordId);
    }

    public Set<String> ownedSwords(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedSwordsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadSwordOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT sword_id FROM owned_swords WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedSwords failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantSword(UUID uuid, String swordId) {
        ownedSwordsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(swordId);
        writeAsync("grantSword", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_swords(uuid, sword_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, swordId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedSword(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedSwordCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadSwordEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT sword_id FROM equipped_sword WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedSword failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedSword(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedSwordCache.put(uuid, Optional.empty());
        } else {
            equippedSwordCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedSword", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_sword(uuid, sword_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET sword_id = excluded.sword_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedMacePrices(MaceCatalog catalog, List<Integer> pool) {
        upsertPrices("mace_prices", "mace_id",
                catalog.all().stream().map(MaceDefinition::id).toList(), pool, macePrices, "Mace");
    }

    public int macePriceOf(String maceId) {
        return macePrices.getOrDefault(maceId, 100);
    }

    public boolean ownsMace(UUID uuid, String maceId) {
        return ownedMaces(uuid).contains(maceId);
    }

    public Set<String> ownedMaces(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedMacesCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadMaceOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT mace_id FROM owned_maces WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedMaces failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantMace(UUID uuid, String maceId) {
        ownedMacesCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(maceId);
        writeAsync("grantMace", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_maces(uuid, mace_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, maceId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedMace(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedMaceCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadMaceEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT mace_id FROM equipped_mace WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedMace failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedMace(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedMaceCache.put(uuid, Optional.empty());
        } else {
            equippedMaceCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedMace", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_mace(uuid, mace_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET mace_id = excluded.mace_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedTitlePrices(TitleCatalog catalog, List<Integer> pool) {
        upsertPrices("title_prices", "title_id",
                catalog.all().stream().map(TitleDefinition::id).toList(), pool, titlePrices, "Title");
    }

    public int titlePriceOf(String titleId) {
        return titlePrices.getOrDefault(titleId, 100);
    }

    public boolean ownsTitle(UUID uuid, String titleId) {
        return ownedTitles(uuid).contains(titleId);
    }

    public Set<String> ownedTitles(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedTitlesCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadTitleOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT title_id FROM owned_titles WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedTitles failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantTitle(UUID uuid, String titleId) {
        ownedTitlesCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(titleId);
        writeAsync("grantTitle", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_titles(uuid, title_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, titleId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedTitle(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedTitleCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadTitleEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT title_id FROM equipped_title WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedTitle failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedTitle(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedTitleCache.put(uuid, Optional.empty());
        } else {
            equippedTitleCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedTitle", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_title(uuid, title_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET title_id = excluded.title_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedTitleColorPrices(TitleColorCatalog catalog, List<Integer> pool) {
        upsertPrices("title_color_prices", "color_id",
                catalog.all().stream().map(TitleColorDefinition::id).toList(), pool, titleColorPrices, "TitleColor");
    }

    public int titleColorPriceOf(String colorId) {
        return titleColorPrices.getOrDefault(colorId, 100);
    }

    public boolean ownsTitleColor(UUID uuid, String colorId) {
        return ownedTitleColors(uuid).contains(colorId);
    }

    public Set<String> ownedTitleColors(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedTitleColorsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadTitleColorOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT color_id FROM owned_title_colors WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedTitleColors failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantTitleColor(UUID uuid, String colorId) {
        ownedTitleColorsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(colorId);
        writeAsync("grantTitleColor", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_title_colors(uuid, color_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, colorId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedTitleColor(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedTitleColorCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadTitleColorEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT color_id FROM equipped_title_color WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedTitleColor failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedTitleColor(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedTitleColorCache.put(uuid, Optional.empty());
        } else {
            equippedTitleColorCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedTitleColor", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_title_color(uuid, color_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET color_id = excluded.color_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedKillEffectPrices(KillEffectCatalog catalog, List<Integer> pool) {
        upsertPrices("kill_effect_prices", "effect_id",
                catalog.all().stream().map(KillEffectDefinition::id).toList(), pool, killEffectPrices, "KillEffect");
    }

    public int killEffectPriceOf(String effectId) {
        return killEffectPrices.getOrDefault(effectId, 100);
    }

    public boolean ownsKillEffect(UUID uuid, String effectId) {
        return ownedKillEffects(uuid).contains(effectId);
    }

    public Set<String> ownedKillEffects(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedKillEffectsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadKillEffectOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT effect_id FROM owned_kill_effects WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedKillEffects failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantKillEffect(UUID uuid, String effectId) {
        ownedKillEffectsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(effectId);
        writeAsync("grantKillEffect", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_kill_effects(uuid, effect_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, effectId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedKillEffect(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedKillEffectCache.getOrDefault(uuid, Optional.empty());
    }

    public boolean isKillEffectCached(UUID uuid) {
        return equippedKillEffectCache.containsKey(uuid);
    }

    private Optional<String> loadKillEffectEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT effect_id FROM equipped_kill_effect WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedKillEffect failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedKillEffect(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedKillEffectCache.put(uuid, Optional.empty());
        } else {
            equippedKillEffectCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedKillEffect", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_kill_effect(uuid, effect_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET effect_id = excluded.effect_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public void seedTagPrices(TagCatalog catalog, List<Integer> pool) {
        upsertPrices("tag_prices", "tag_id",
                catalog.all().stream().map(TagDefinition::id).toList(), pool, tagPrices, "Tag");
    }

    public int tagPriceOf(String tagId) {
        return tagPrices.getOrDefault(tagId, 100);
    }

    public boolean ownsTag(UUID uuid, String tagId) {
        return ownedTags(uuid).contains(tagId);
    }

    public Set<String> ownedTags(UUID uuid) {
        ensurePlayerCached(uuid);
        return ownedTagsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> loadTagOwned(UUID uuid) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT tag_id FROM owned_tags WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("ownedTags failed: " + ex.getMessage());
        }
        return out;
    }

    public void grantTag(UUID uuid, String tagId) {
        ownedTagsCache.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(tagId);
        writeAsync("grantTag", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT OR IGNORE INTO owned_tags(uuid, tag_id, purchased_at) VALUES(?, ?, ?)"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, tagId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedTag(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedTagCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadTagEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT tag_id FROM equipped_tag WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedTag failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedTag(UUID uuid, String idOrNull) {
        if (idOrNull == null || idOrNull.isBlank()) {
            equippedTagCache.put(uuid, Optional.empty());
        } else {
            equippedTagCache.put(uuid, Optional.of(idOrNull));
        }
        final String persist = idOrNull;
        writeAsync("setEquippedTag", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_tag(uuid, tag_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET tag_id = excluded.tag_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

    public Optional<String> equippedCage(UUID uuid) {
        ensurePlayerCached(uuid);
        return equippedCageCache.getOrDefault(uuid, Optional.empty());
    }

    private Optional<String> loadCageEquipped(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT cage_id FROM equipped_cage WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (id != null && !id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("equippedCage failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public void setEquippedCage(UUID uuid, String cageIdOrNull) {
        if (cageIdOrNull == null || cageIdOrNull.isBlank()) {
            equippedCageCache.put(uuid, Optional.empty());
        } else {
            equippedCageCache.put(uuid, Optional.of(cageIdOrNull));
        }
        final String persist = cageIdOrNull;
        writeAsync("setEquippedCage", () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    sql("INSERT INTO equipped_cage(uuid, cage_id) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET cage_id = excluded.cage_id"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, persist);
                ps.executeUpdate();
            }
        });
    }

}
