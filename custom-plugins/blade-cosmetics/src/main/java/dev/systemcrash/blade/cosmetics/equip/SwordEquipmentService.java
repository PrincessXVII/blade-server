package dev.systemcrash.blade.cosmetics.equip;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.sword.SwordCatalog;
import dev.systemcrash.blade.cosmetics.sword.SwordDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Optional;

public final class SwordEquipmentService implements Listener {
    public static final String PDC_SWORD_ID = "sword_id";
    /** Atlantis sword skins; legendary weapons use lower CMDs (1–14). */
    public static final int COSMETIC_CMD_MIN = 7001;
    public static final int COSMETIC_CMD_MAX = 7254;

    private final BladeCosmeticsPlugin plugin;
    private final SwordCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey swordKey;
    private BukkitTask watchdog;

    private Object legendaryManager;
    private Method isLegendaryMethod;

    public SwordEquipmentService(
            BladeCosmeticsPlugin plugin,
            SwordCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.swordKey = new NamespacedKey(plugin, PDC_SWORD_ID);
        resolveLegendaryChecker();
    }

    public void start() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
    }

    public void stop() {
        if (watchdog != null) {
            watchdog.cancel();
        }
    }

    public void equip(Player player, String swordId) {
        repository.setEquippedSword(player.getUniqueId(), swordId);
        apply(player);
    }

    public void clear(Player player) {
        repository.setEquippedSword(player.getUniqueId(), null);
        stripAll(player);
    }

    public void apply(Player player) {
        Optional<String> equipped = repository.equippedSword(player.getUniqueId());
        if (equipped.isEmpty() || isDisabledWorld(player.getWorld().getName())) {
            stripAll(player);
            return;
        }
        catalog.find(equipped.get()).ifPresent(sword -> paintInventory(player, sword));
    }

    public ItemStack createGuiIcon(SwordDefinition sword, boolean owned, int price) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(sword.cmd());
        meta.displayName(mm("<!italic><color:" + sword.color() + ">" + escape(sword.name()) + "</color>"));
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, sword.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(sword.description()) + "</color>"));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый ᴄᴋиʜ:</color> <color:#4EEA65>дᴏᴄтупᴇʜ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый ᴄᴋиʜ:</color> <color:#EA4E4E>ʜᴇдᴏᴄтупᴇʜ</color>"));
            lore.add(mm("<!italic><color:#FFFFFF>ᴄтᴏимᴏᴄть:</color> <color:#4498DB>" + price
                    + "</color> <color:#AAAAAA>ᴏᴘбᴏʙ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжмитᴇ</color><color:#AAAAAA>, чтᴏбы ᴋупить</color>"));
            lore.add(Component.empty());
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void paintInventory(Player player, SwordDefinition sword) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            ItemStack updated = paintIfEligible(stack, sword);
            if (updated != null) {
                inv.setItem(i, updated);
            }
        }
        ItemStack off = paintIfEligible(inv.getItemInOffHand(), sword);
        if (off != null) {
            inv.setItemInOffHand(off);
        }
    }

    private ItemStack paintIfEligible(ItemStack stack, SwordDefinition sword) {
        if (!isSkinnableSword(stack) || isLegendaryWeapon(stack)) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        boolean tagged = meta.getPersistentDataContainer().has(swordKey, PersistentDataType.STRING);
        Integer cmd = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        // Non-cosmetic custom model without our tag = legendary / other pack item.
        if (cmd != null && (cmd < COSMETIC_CMD_MIN || cmd > COSMETIC_CMD_MAX) && !tagged) {
            return null;
        }
        String current = meta.getPersistentDataContainer().get(swordKey, PersistentDataType.STRING);
        if (sword.id().equals(current) && cmd != null && cmd == sword.cmd()) {
            return null;
        }
        meta.setCustomModelData(sword.cmd());
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, sword.id());
        // Keep display name, lore, enchants — only visual model changes.
        stack.setItemMeta(meta);
        return stack;
    }

    private void stripAll(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            ItemStack updated = stripIfCosmetic(stack);
            if (updated != null) {
                inv.setItem(i, updated);
            }
        }
        ItemStack off = stripIfCosmetic(inv.getItemInOffHand());
        if (off != null) {
            inv.setItemInOffHand(off);
        }
    }

    private ItemStack stripIfCosmetic(ItemStack stack) {
        if (!isSkinnableSword(stack) || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.getPersistentDataContainer().has(swordKey, PersistentDataType.STRING)) {
            return null;
        }
        meta.getPersistentDataContainer().remove(swordKey);
        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd >= COSMETIC_CMD_MIN && cmd <= COSMETIC_CMD_MAX) {
                boolean cleared = false;
                try {
                    Method unset = ItemMeta.class.getMethod("setCustomModelData", Integer.class);
                    unset.invoke(meta, new Object[]{null});
                    cleared = true;
                } catch (ReflectiveOperationException ignored) {
                }
                if (!cleared) {
                    ItemStack plain = new ItemStack(stack.getType(), stack.getAmount());
                    ItemMeta plainMeta = plain.getItemMeta();
                    plainMeta.displayName(meta.displayName());
                    plainMeta.lore(meta.lore());
                    if (!meta.getEnchants().isEmpty()) {
                        meta.getEnchants().forEach((ench, level) -> plainMeta.addEnchant(ench, level, true));
                    }
                    plainMeta.setUnbreakable(meta.isUnbreakable());
                    for (ItemFlag flag : meta.getItemFlags()) {
                        plainMeta.addItemFlags(flag);
                    }
                    if (meta instanceof org.bukkit.inventory.meta.Damageable damaged
                            && plainMeta instanceof org.bukkit.inventory.meta.Damageable plainDamaged) {
                        plainDamaged.setDamage(damaged.getDamage());
                    }
                    plain.setItemMeta(plainMeta);
                    return plain;
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isSkinnableSword(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        return type == Material.DIAMOND_SWORD || type == Material.NETHERITE_SWORD;
    }

    public boolean isLegendaryWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        if (legendaryManager != null && isLegendaryMethod != null) {
            try {
                Object result = isLegendaryMethod.invoke(legendaryManager, item);
                if (result instanceof Boolean b && b) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd < COSMETIC_CMD_MIN || cmd > COSMETIC_CMD_MAX) {
                return !meta.getPersistentDataContainer().has(swordKey, PersistentDataType.STRING);
            }
        }
        return false;
    }

    private void resolveLegendaryChecker() {
        Plugin weapons = Bukkit.getPluginManager().getPlugin("BladeWeapons");
        if (weapons == null || !weapons.isEnabled()) {
            return;
        }
        try {
            Method legacyBridge = weapons.getClass().getMethod("legacyBridge");
            Object bridge = legacyBridge.invoke(weapons);
            if (bridge == null) {
                return;
            }
            Method getManager = bridge.getClass().getMethod("getSlotCooldownDisplayManager");
            legendaryManager = getManager.invoke(bridge);
            if (legendaryManager != null) {
                isLegendaryMethod = legendaryManager.getClass()
                        .getMethod("isLegendaryWeapon", ItemStack.class);
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().info("BladeWeapons legendary check unavailable; using CMD fallback.");
        }
    }

    private boolean isDisabledWorld(String world) {
        return plugin.getConfig().getStringList("disabled-worlds").stream()
                .anyMatch(w -> w.equalsIgnoreCase(world));
    }

    private void tickAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof
                    dev.systemcrash.blade.cosmetics.gui.CosmeticsMenuHolder) {
                continue;
            }
            Optional<String> equipped = repository.equippedSword(player.getUniqueId());
            if (equipped.isEmpty()) {
                continue;
            }
            // Cache hit only — paint if inventory has unpainted skinnable swords.
            catalog.find(equipped.get()).ifPresent(sword -> paintInventory(player, sword));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInv(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Skip cosmetics/shop GUIs — full inv paint on every click causes hitch.
        if (event.getInventory().getHolder() instanceof
                dev.systemcrash.blade.cosmetics.gui.CosmeticsMenuHolder) {
            return;
        }
        String holderName = event.getInventory().getHolder() == null
                ? ""
                : event.getInventory().getHolder().getClass().getName();
        if (holderName.endsWith("ShopHolder") || holderName.contains("ShopService$ShopHolder")) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> apply(player));
        }
    }

    private static Component mm(String mini) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
    }

    private static String escape(String raw) {
        return raw.replace("<", "").replace(">", "");
    }
}
