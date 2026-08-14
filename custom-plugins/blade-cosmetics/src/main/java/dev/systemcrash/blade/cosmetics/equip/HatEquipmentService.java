package dev.systemcrash.blade.cosmetics.equip;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.hat.HatCatalog;
import dev.systemcrash.blade.cosmetics.hat.HatDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HatEquipmentService implements Listener {
    public static final String PDC_HAT_ID = "hat_id";
    public static final String DEFAULT_HAT_ID = "army_helmet/default";

    private final BladeCosmeticsPlugin plugin;
    private final HatCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey hatKey;
    private final NamespacedKey armorKey;
    private final NamespacedKey toughnessKey;
    private BukkitTask watchdog;

    public HatEquipmentService(
            BladeCosmeticsPlugin plugin,
            HatCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.hatKey = new NamespacedKey(plugin, PDC_HAT_ID);
        this.armorKey = new NamespacedKey(plugin, "cosmetic_hat_armor");
        this.toughnessKey = new NamespacedKey(plugin, "cosmetic_hat_toughness");
    }

    public void start() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
    }

    public void stop() {
        if (watchdog != null) {
            watchdog.cancel();
        }
    }

    public NamespacedKey hatKey() {
        return hatKey;
    }

    public void equip(Player player, String hatId) {
        repository.grantHat(player.getUniqueId(), hatId);
        repository.setEquippedHat(player.getUniqueId(), hatId);
        apply(player);
    }

    public void clear(Player player) {
        // Clearing cosmetics returns to the default army helmet (same as new players).
        UUID uuid = player.getUniqueId();
        repository.grantHat(uuid, DEFAULT_HAT_ID);
        repository.setEquippedHat(uuid, DEFAULT_HAT_ID);
        forceEquip(player, DEFAULT_HAT_ID);
    }

    /** Apply now and again after hub inventory clears (join / world change). */
    public void applySoon(Player player) {
        apply(player);
        for (long delay : new long[] {1L, 5L, 20L, 40L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    apply(player);
                }
            }, delay);
        }
    }

    public void apply(Player player) {
        Optional<String> equipped = repository.equippedHat(player.getUniqueId());
        String hatId = equipped.filter(id -> !id.isBlank()).orElse(DEFAULT_HAT_ID);
        // Memory-only default — never sync grantHat/setEquipped on join hot path.
        if (equipped.isEmpty() || equipped.get().isBlank()) {
            ensureDefaultHatAsync(player.getUniqueId());
        }
        if (isDisabledWorld(player.getWorld().getName())) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (isCosmeticHat(helmet)) {
                player.getInventory().setHelmet(null);
            }
            return;
        }
        forceEquip(player, hatId);
    }

    /** Put the given hat on the head, falling back to army helmet if the id is unknown. */
    private void forceEquip(Player player, String hatId) {
        if (isDisabledWorld(player.getWorld().getName())) {
            return;
        }
        String id = (hatId == null || hatId.isBlank()) ? DEFAULT_HAT_ID : hatId;
        HatDefinition hat = catalog.find(id).orElse(null);
        if (hat == null && !DEFAULT_HAT_ID.equals(id)) {
            repository.setEquippedHat(player.getUniqueId(), DEFAULT_HAT_ID);
            hat = catalog.find(DEFAULT_HAT_ID).orElse(null);
        }
        if (hat == null) {
            plugin.getLogger().warning("Default hat missing from catalog: " + DEFAULT_HAT_ID);
            return;
        }
        player.getInventory().setHelmet(createWornHat(player, hat));
    }

    private void ensureDefaultHatAsync(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> owned = repository.ownedHats(uuid);
            if (!owned.contains(DEFAULT_HAT_ID)) {
                repository.grantHat(uuid, DEFAULT_HAT_ID);
            }
            Optional<String> equipped = repository.equippedHat(uuid);
            if (equipped.isEmpty() || equipped.get().isBlank()) {
                repository.setEquippedHat(uuid, DEFAULT_HAT_ID);
            }
        });
    }

    public ItemStack createGuiIcon(HatDefinition hat, boolean owned, int price) {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(hat.cmd());
        meta.displayName(mm("<!italic><color:" + hat.color() + ">" + escape(hat.name()) + "</color>"));
        meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, hat.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(hat.description()) + "</color>"));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜᴀя шляпᴀ:</color> <color:#4EEA65>дᴏᴄтупʜᴀ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜᴀя шляпᴀ:</color> <color:#EA4E4E>ʜᴇдᴏᴄтʏпʜᴀ</color>"));
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

    private ItemStack createWornHat(Player player, HatDefinition hat) {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(hat.cmd());
        meta.displayName(Component.space().decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, hat.id());
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(false);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_ENCHANTS);

        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeEnchant(Enchantment.PROTECTION);

        boolean meetup = isMeetupWorld(player.getWorld().getName());
        // Diamond helmet stats everywhere; netherite + Protection IV on meetups.
        double armor = 3.0;
        double toughness = meetup ? 3.0 : 2.0;
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                armorKey, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                toughnessKey, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        if (meetup) {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCosmeticHat(ItemStack item) {
        if (item == null || item.getType() != Material.CARVED_PUMPKIN || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.STRING);
    }

    public Optional<String> hatIdOf(ItemStack item) {
        if (!isCosmeticHat(item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(hatKey, PersistentDataType.STRING));
    }

    private void tickAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack helmet = player.getInventory().getHelmet();
            // Cold cache: never strip — async prefetch has not finished yet.
            // Still keep a visible default hat; hub clear otherwise leaves the slot empty.
            if (!repository.isHatStateCached(player.getUniqueId())) {
                if (!isDisabledWorld(player.getWorld().getName()) && !isCosmeticHat(helmet)) {
                    forceEquip(player, DEFAULT_HAT_ID);
                }
                continue;
            }
            Optional<String> equipped = repository.equippedHat(player.getUniqueId());
            if (equipped.isEmpty()) {
                // Cached and explicitly unequipped — still keep default hat look.
                if (!isCosmeticHat(helmet)) {
                    forceEquip(player, DEFAULT_HAT_ID);
                }
                continue;
            }
            if (isDisabledWorld(player.getWorld().getName())) {
                if (isCosmeticHat(helmet)) {
                    player.getInventory().setHelmet(null);
                }
                continue;
            }
            if (!isCosmeticHat(helmet) || !equipped.get().equals(hatIdOf(helmet).orElse(null))) {
                forceEquip(player, equipped.get());
                continue;
            }
            boolean meetup = isMeetupWorld(player.getWorld().getName());
            boolean hasProt = helmet.getEnchantmentLevel(Enchantment.PROTECTION) >= 4;
            if (meetup != hasProt) {
                forceEquip(player, equipped.get());
            }
        }
    }

    private boolean isDisabledWorld(String world) {
        return plugin.getConfig().getStringList("disabled-worlds").stream()
                .anyMatch(w -> w.equalsIgnoreCase(world));
    }

    private boolean isMeetupWorld(String world) {
        String lower = world.toLowerCase(Locale.ROOT);
        return plugin.getConfig().getStringList("meetup-prefixes").stream()
                .anyMatch(prefix -> lower.startsWith(prefix.toLowerCase(Locale.ROOT)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Next tick after hub clear / kit give — cache should already be warm from prelogin.
        plugin.getServer().getScheduler().runTask(plugin, () -> applySoon(event.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        applySoon(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isCosmeticHat(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isCosmeticHat(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean involvesHat = isCosmeticHat(current) || isCosmeticHat(cursor);
        if (!involvesHat) {
            return;
        }
        // Never allow moving/unequipping the cosmetic hat via inventory.
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isCosmeticHat);
        ItemStack helmet = event.getEntity().getInventory().getHelmet();
        if (isCosmeticHat(helmet)) {
            event.getEntity().getInventory().setHelmet(null);
        }
    }

    private static Component mm(String mini) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mini);
    }

    private static String escape(String raw) {
        return raw.replace("<", "").replace(">", "");
    }

}
