package dev.systemcrash.blade;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PermissionLimits {
    private static final String MUTE_MAX_PREFIX = "blade.mute.max.";
    private static final String BAN_MAX_PREFIX = "blade.ban.max.";

    private PermissionLimits() {
    }

    public static long maxMuteSeconds(CommandSender sender) {
        if (sender.hasPermission("blade.admin") || sender.hasPermission("blade.mute.unlimited")) {
            return Long.MAX_VALUE;
        }

        long max = 0L;
        for (PermissionAttachmentInfo info : sender.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission();
            if (!permission.startsWith(MUTE_MAX_PREFIX)) {
                continue;
            }
            String suffix = permission.substring(MUTE_MAX_PREFIX.length());
            try {
                max = Math.max(max, Long.parseLong(suffix));
            } catch (NumberFormatException ignored) {
                // Ignore malformed nodes.
            }
        }
        return max;
    }

    public static long maxBanSeconds(CommandSender sender) {
        if (sender.hasPermission("blade.admin")) {
            return Long.MAX_VALUE;
        }
        if (sender.hasPermission("blade.ban.permanent")) {
            return -1L;
        }

        long max = 0L;
        for (PermissionAttachmentInfo info : sender.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission();
            if (!permission.startsWith(BAN_MAX_PREFIX)) {
                continue;
            }
            String suffix = permission.substring(BAN_MAX_PREFIX.length());
            try {
                max = Math.max(max, Long.parseLong(suffix));
            } catch (NumberFormatException ignored) {
                // Ignore malformed nodes.
            }
        }
        return max;
    }

    public static boolean canMute(CommandSender sender) {
        return sender.hasPermission("blade.admin") || sender.hasPermission("blade.mute");
    }

    public static boolean canBan(CommandSender sender) {
        return sender.hasPermission("blade.admin") || sender.hasPermission("blade.ban");
    }

    public static void validateMuteDuration(CommandSender sender, long requestedSeconds) {
        if (requestedSeconds == -1L && !sender.hasPermission("blade.mute.unlimited") && !sender.hasPermission("blade.admin")) {
            throw new IllegalArgumentException("You cannot issue permanent mutes.");
        }
        if (requestedSeconds == -1L) {
            return;
        }
        long max = maxMuteSeconds(sender);
        if (max <= 0L) {
            throw new IllegalArgumentException("You do not have permission to mute players.");
        }
        if (requestedSeconds > max) {
            throw new IllegalArgumentException("Maximum mute duration for you is " + DurationParser.formatSeconds(max) + ".");
        }
    }

    public static void validateBanDuration(CommandSender sender, long requestedSeconds) {
        if (requestedSeconds == -1L) {
            if (!sender.hasPermission("blade.ban.permanent") && !sender.hasPermission("blade.admin")) {
                throw new IllegalArgumentException("You cannot issue permanent bans.");
            }
            return;
        }
        long max = maxBanSeconds(sender);
        if (max == -1L) {
            return;
        }
        if (max <= 0L) {
            throw new IllegalArgumentException("You do not have permission to ban players.");
        }
        if (requestedSeconds > max) {
            throw new IllegalArgumentException("Maximum ban duration for you is " + DurationParser.formatSeconds(max) + ".");
        }
    }
}
