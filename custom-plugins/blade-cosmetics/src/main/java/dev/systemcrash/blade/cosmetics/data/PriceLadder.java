package dev.systemcrash.blade.cosmetics.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pyramid orb prices: cheaper tiers get more items, expensive tiers fewer. */
public final class PriceLadder {
    private PriceLadder() {
    }

    public static Map<String, Integer> assign(List<String> ids, List<Integer> pool) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("price pool is empty");
        }
        List<Integer> sortedPool = new ArrayList<>(pool);
        Collections.sort(sortedPool);
        int k = sortedPool.size();
        int[] weights = new int[k];
        int sumW = 0;
        for (int i = 0; i < k; i++) {
            // Cheap (index 0) heaviest; top tier still appears a few times.
            weights[i] = k - i;
            sumW += weights[i];
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : ids) {
            int h = Math.floorMod(id.hashCode(), sumW);
            int acc = 0;
            int idx = k - 1;
            for (int i = 0; i < k; i++) {
                acc += weights[i];
                if (h < acc) {
                    idx = i;
                    break;
                }
            }
            out.put(id, sortedPool.get(idx));
        }
        return out;
    }
}
