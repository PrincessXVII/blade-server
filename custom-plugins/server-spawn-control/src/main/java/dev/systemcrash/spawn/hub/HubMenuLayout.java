package dev.systemcrash.spawn.hub;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class HubMenuLayout {
    public static final int MAIN_MENU_SIZE = 27;
    public static final int ARENA_MENU_SIZE = 9;
    public static final int ARENA_COUNT = 9;

    private static final int[] MEETUPS_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] BKB_SLOTS = {3, 4, 5, 6, 12, 13, 14, 15, 21, 22, 23, 24};
    private static final int[] SMP_SLOTS = {7, 8, 16, 17, 25, 26};
    private static final int[] SMP_TILE_MAP = {0, 1, 3, 4, 6, 7};

    private HubMenuLayout() {
    }

    public static Set<Integer> allMainMenuSlots() {
        Set<Integer> slots = new HashSet<>();
        Arrays.stream(MEETUPS_SLOTS).forEach(slots::add);
        Arrays.stream(BKB_SLOTS).forEach(slots::add);
        Arrays.stream(SMP_SLOTS).forEach(slots::add);
        return slots;
    }

    public static boolean isMeetupsSlot(int slot) {
        return indexOf(MEETUPS_SLOTS, slot) >= 0;
    }

    public static boolean isBkbSlot(int slot) {
        return indexOf(BKB_SLOTS, slot) >= 0;
    }

    public static boolean isSmpSlot(int slot) {
        return indexOf(SMP_SLOTS, slot) >= 0;
    }

    public static int meetupsTileIndex(int slot) {
        return indexOf(MEETUPS_SLOTS, slot);
    }

    public static int bkbTileIndex(int slot) {
        return indexOf(BKB_SLOTS, slot);
    }

    public static int smpTileIndex(int slot) {
        int index = indexOf(SMP_SLOTS, slot);
        if (index < 0) {
            return -1;
        }
        return SMP_TILE_MAP[index];
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
