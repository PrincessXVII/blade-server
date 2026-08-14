package dev.systemcrash.blade.cosmetics.killeffect;

import java.util.List;

public record KillEffectDefinition(
        String id,
        String name,
        String description,
        String material,
        String sound,
        List<Integer> parts
) {
}
