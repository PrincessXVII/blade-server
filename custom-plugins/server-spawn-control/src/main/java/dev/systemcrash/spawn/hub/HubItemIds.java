package dev.systemcrash.spawn.hub;

public final class HubItemIds {
    public static final int CHOOSE_SERVER = 7000;
    public static final int ARENA_AVAILABLE = 7031;
    public static final int ARENA_UNAVAILABLE = 7032;

    private static final int MEETUPS_BASE = 7001;
    private static final int BKB_BASE = 7010;
    private static final int SMP_BASE = 7022;

    private HubItemIds() {
    }

    public static int meetupsTile(int row, int col) {
        return MEETUPS_BASE + row * 3 + col;
    }

    public static int bkbTile(int row, int col) {
        return BKB_BASE + row * 4 + col;
    }

    public static int smpTile(int row, int col) {
        return SMP_BASE + row * 3 + col;
    }
}
