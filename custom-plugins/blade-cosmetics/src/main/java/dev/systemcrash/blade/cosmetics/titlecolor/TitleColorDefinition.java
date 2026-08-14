package dev.systemcrash.blade.cosmetics.titlecolor;

import java.util.List;

public record TitleColorDefinition(
        String id,
        String name,
        String description,
        String material,
        List<String> colors
) {
}
