package dev.systemcrash.blade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentManager {
    private final File file;
    private final Map<UUID, PunishmentRecord> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, PunishmentRecord> bans = new ConcurrentHashMap<>();
    private final Map<String, PunishmentRecord> ipBans = new ConcurrentHashMap<>();

    public PunishmentManager(File file) {
        this.file = file;
        load();
    }

    public void load() {
        mutes.clear();
        bans.clear();
        ipBans.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadSection(yaml.getConfigurationSection("mutes"), mutes, false);
        loadSection(yaml.getConfigurationSection("bans"), bans, false);
        loadSection(yaml.getConfigurationSection("ip-bans"), ipBans, true);
    }

    private void loadSection(ConfigurationSection section, Map<?, PunishmentRecord> target, boolean byIp) {
        if (section == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            PunishmentRecord record = fromSection(entry);
            if (!record.isActive(now)) {
                continue;
            }
            if (byIp) {
                ((Map<String, PunishmentRecord>) target).put(key, record);
            } else {
                ((Map<UUID, PunishmentRecord>) target).put(UUID.fromString(key), record);
            }
        }
    }

    private PunishmentRecord fromSection(ConfigurationSection entry) {
        return new PunishmentRecord(
                UUID.fromString(entry.getString("uuid")),
                entry.getString("name", "unknown"),
                entry.getString("staff", "Console"),
                entry.getString("reason", "No reason"),
                entry.getLong("created-at"),
                entry.getLong("expires-at"),
                entry.getBoolean("ip-ban", false),
                entry.getString("ip", "")
        );
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        writeSection(yaml.createSection("mutes"), mutes);
        writeSection(yaml.createSection("bans"), bans);
        ConfigurationSection ipSection = yaml.createSection("ip-bans");
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PunishmentRecord> entry : ipBans.entrySet()) {
            if (entry.getValue().isActive(now)) {
                writeRecord(ipSection.createSection(entry.getKey()), entry.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save punishments.", exception);
        }
    }

    private void writeSection(ConfigurationSection section, Map<UUID, PunishmentRecord> records) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PunishmentRecord> entry : records.entrySet()) {
            if (entry.getValue().isActive(now)) {
                writeRecord(section.createSection(entry.getKey().toString()), entry.getValue());
            }
        }
    }

    private void writeRecord(ConfigurationSection section, PunishmentRecord record) {
        section.set("uuid", record.getTargetUuid().toString());
        section.set("name", record.getTargetName());
        section.set("staff", record.getStaffName());
        section.set("reason", record.getReason());
        section.set("created-at", record.getCreatedAt());
        section.set("expires-at", record.getExpiresAt());
        section.set("ip-ban", record.isIpBan());
        section.set("ip", record.getIpAddress());
    }

    public PunishmentRecord mute(UUID uuid, String name, String staff, String reason, long durationSeconds) {
        long now = System.currentTimeMillis();
        long expiresAt = durationSeconds < 0L ? -1L : now + durationSeconds * 1000L;
        PunishmentRecord record = new PunishmentRecord(uuid, name, staff, reason, now, expiresAt, false, "");
        mutes.put(uuid, record);
        save();
        return record;
    }

    public PunishmentRecord ban(UUID uuid, String name, String staff, String reason, long durationSeconds, String ip) {
        long now = System.currentTimeMillis();
        long expiresAt = durationSeconds < 0L ? -1L : now + durationSeconds * 1000L;
        PunishmentRecord record = new PunishmentRecord(uuid, name, staff, reason, now, expiresAt, false, ip == null ? "" : ip);
        bans.put(uuid, record);
        save();
        return record;
    }

    public PunishmentRecord ipBan(UUID uuid, String name, String staff, String reason, String ip) {
        long now = System.currentTimeMillis();
        PunishmentRecord record = new PunishmentRecord(uuid, name, staff, reason, now, -1L, true, ip);
        bans.put(uuid, record);
        ipBans.put(ip, record);
        save();
        return record;
    }

    public boolean unmute(UUID uuid) {
        boolean removed = mutes.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean unban(UUID uuid) {
        PunishmentRecord record = bans.remove(uuid);
        if (record == null) {
            return false;
        }
        if (record.isIpBan() && record.getIpAddress() != null && !record.getIpAddress().isBlank()) {
            ipBans.remove(record.getIpAddress());
        }
        save();
        return true;
    }

    public PunishmentRecord getActiveMute(UUID uuid) {
        PunishmentRecord record = mutes.get(uuid);
        if (record == null) {
            return null;
        }
        if (!record.isActive(System.currentTimeMillis())) {
            mutes.remove(uuid);
            save();
            return null;
        }
        return record;
    }

    public PunishmentRecord getActiveBan(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        if (ip != null && !ip.isBlank()) {
            PunishmentRecord ipRecord = ipBans.get(ip);
            if (ipRecord != null && ipRecord.isActive(now)) {
                return ipRecord;
            }
        }
        PunishmentRecord record = bans.get(uuid);
        if (record == null) {
            return null;
        }
        if (!record.isActive(now)) {
            bans.remove(uuid);
            save();
            return null;
        }
        return record;
    }

    public List<PunishmentRecord> allActiveBans() {
        long now = System.currentTimeMillis();
        List<PunishmentRecord> active = new ArrayList<>();
        for (PunishmentRecord record : bans.values()) {
            if (record.isActive(now)) {
                active.add(record);
            }
        }
        return active;
    }
}
