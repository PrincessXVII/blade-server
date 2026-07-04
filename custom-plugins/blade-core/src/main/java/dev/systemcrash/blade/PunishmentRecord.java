package dev.systemcrash.blade;

import java.util.UUID;

public final class PunishmentRecord {
    private final UUID targetUuid;
    private final String targetName;
    private final String staffName;
    private final String reason;
    private final long createdAt;
    private final long expiresAt;
    private final boolean ipBan;
    private final String ipAddress;

    public PunishmentRecord(
            UUID targetUuid,
            String targetName,
            String staffName,
            String reason,
            long createdAt,
            long expiresAt,
            boolean ipBan,
            String ipAddress
    ) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.staffName = staffName;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ipBan = ipBan;
        this.ipAddress = ipAddress;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getStaffName() {
        return staffName;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isIpBan() {
        return ipBan;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isPermanent() {
        return expiresAt < 0L;
    }

    public boolean isActive(long now) {
        return isPermanent() || expiresAt > now;
    }
}
