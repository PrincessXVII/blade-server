package dev.systemcrash.blade.cosmetics.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class CosmeticsMenuHolder implements InventoryHolder {
    public enum Type {
        MAIN,
        HATS,
        SWORDS,
        MACES,
        TITLES,
        TITLE_COLORS,
        KILL_EFFECTS,
        TAGS,
        CAGES
    }

    private final Type type;
    private final int page;
    private Inventory inventory;

    public CosmeticsMenuHolder(Type type, int page) {
        this.type = type;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public int page() {
        return page;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
