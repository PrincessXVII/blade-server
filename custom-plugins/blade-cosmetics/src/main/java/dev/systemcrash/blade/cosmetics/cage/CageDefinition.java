package dev.systemcrash.blade.cosmetics.cage;

public record CageDefinition(
        String id,
        String name,
        String display,
        String description,
        String material,
        String schematic,
        String min_rank,
        int min_weight,
        String rank_label
) {
    public String minRank() {
        return min_rank;
    }

    public int minWeight() {
        return min_weight;
    }

    public String rankLabel() {
        return rank_label == null || rank_label.isBlank() ? min_rank : rank_label;
    }
}
