package dev.systemcrash.blade;

public final class DurationParser {
    private DurationParser() {
    }

    public static long parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration is empty.");
        }

        String value = input.trim().toLowerCase();
        if (value.equals("perm") || value.equals("permanent") || value.equals("forever")) {
            return -1L;
        }

        long multiplier = 1L;
        if (value.endsWith("s")) {
            multiplier = 1L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 60L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("h")) {
            multiplier = 3600L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("d")) {
            multiplier = 86400L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("w")) {
            multiplier = 604800L;
            value = value.substring(0, value.length() - 1);
        }

        long amount = Long.parseLong(value);
        if (amount <= 0L) {
            throw new IllegalArgumentException("Duration must be positive.");
        }
        return amount * multiplier;
    }

    public static String formatSeconds(long seconds) {
        if (seconds < 0L) {
            return "permanent";
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0L) {
            return days + "d";
        }
        if (hours > 0L) {
            return hours + "h";
        }
        if (minutes > 0L) {
            return minutes + "m";
        }
        return seconds + "s";
    }
}
