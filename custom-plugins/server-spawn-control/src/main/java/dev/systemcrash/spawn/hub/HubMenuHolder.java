package dev.systemcrash.spawn.hub;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HubMenuHolder implements InventoryHolder {
    private final HubMenuType type;

    public HubMenuHolder(HubMenuType type) {
        this.type = type;
    }

    public HubMenuType type() {
        return type;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
