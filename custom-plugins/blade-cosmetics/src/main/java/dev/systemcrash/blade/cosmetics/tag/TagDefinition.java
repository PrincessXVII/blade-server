package dev.systemcrash.blade.cosmetics.tag;

public record TagDefinition(
        String id,
        String name,
        String display,
        String description,
        String material,
        String color,
        Integer price
) {
}
