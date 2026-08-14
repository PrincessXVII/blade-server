package dev.systemcrash.blade.cosmetics.tag;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Overhead nametag line via TextDisplay passenger (font mine). */
public final class TagEquipmentService implements Listener {
    public static final String PDC_TAG_ID = "tag_id";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Lobby: nick often hidden (TAB) — sit just above the head. */
    private static final float Y_OFFSET_LOBBY = 0.55f;
    /** Arenas: nick visible — sit one line above the vanilla nametag. */
    private static final float Y_OFFSET_ARENA = 0.95f;
    private static final String LOBBY_WORLD = "lobby";

    private final BladeCosmeticsPlugin plugin;
    private final TagCatalog catalog;
    private final CosmeticsRepository repository;
    private final NamespacedKey tagKey;
    private final Map<UUID, UUID> displays = new ConcurrentHashMap<>();

    public TagEquipmentService(
            BladeCosmeticsPlugin plugin,
            TagCatalog catalog,
            CosmeticsRepository repository
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.repository = repository;
        this.tagKey = new NamespacedKey(plugin, PDC_TAG_ID);
    }

    public void equip(Player player, String tagId) {
        // Tags removed from cosmetics — never equip / never show overhead text.
        repository.setEquippedTag(player.getUniqueId(), null);
        removeDisplay(player);
    }

    public void clear(Player player) {
        repository.setEquippedTag(player.getUniqueId(), null);
        removeDisplay(player);
    }

    public void apply(Player player) {
        // Force-clear any leftover overhead tag displays.
        repository.setEquippedTag(player.getUniqueId(), null);
        removeDisplay(player);
    }

    /** Must run before cross-world teleport — Paper returns false if the player has passengers. */
    public void prepareCrossWorldTeleport(Player player) {
        removeDisplay(player);
    }

    public void stop() {
        for (UUID playerId : displays.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                removeDisplay(player);
            } else {
                UUID displayId = displays.remove(playerId);
                if (displayId != null) {
                    var entity = plugin.getServer().getEntity(displayId);
                    if (entity != null) {
                        entity.remove();
                    }
                }
            }
        }
        displays.clear();
    }

    public ItemStack createGuiIcon(TagDefinition tag, boolean owned, int price) {
        Material material = Material.matchMaterial(tag.material());
        if (material == null || material.isAir()) {
            material = Material.NAME_TAG;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(tag.display())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        var lore = new java.util.ArrayList<Component>();
        lore.add(mm("<!italic><color:#AAAAAA>" + escape(tag.description()) + "</color>"));
        lore.add(Component.empty());
        if (owned) {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый тᴇг:</color> <color:#4EEA65>дᴏᴄтупᴇʜ</color>"));
            lore.add(Component.empty());
            lore.add(mm("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтᴏбы ʜᴀдᴇть</color>"));
            lore.add(Component.empty());
        } else {
            lore.add(mm("<!italic><color:#AAAAAA>дᴀʜʜый тᴇг:</color> <color:#EA4E4E>ʜᴇдᴏᴄтупᴇʜ</color>"));
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

    private void spawnDisplay(Player player, TagDefinition tag) {
        Location loc = player.getLocation();
        TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(MM.deserialize(tag.display()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            // Like vanilla nametags: occlude/darken behind walls, limited view distance.
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            // ~64 blocks if player tracking ≈ 128 (nametag-like).
            d.setViewRange(0.5f);
            d.setPersistent(false);
            d.setInterpolationDuration(0);
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, yOffsetFor(player), 0f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()
            ));
        });
        player.addPassenger(display);
        displays.put(player.getUniqueId(), display.getUniqueId());
    }

    private static float yOffsetFor(Player player) {
        return LOBBY_WORLD.equals(player.getWorld().getName()) ? Y_OFFSET_LOBBY : Y_OFFSET_ARENA;
    }

    private void removeDisplay(Player player) {
        UUID displayId = displays.remove(player.getUniqueId());
        if (displayId == null) {
            // Also clear any leftover passengers tagged as our TextDisplay
            for (var passenger : player.getPassengers()) {
                if (passenger instanceof TextDisplay) {
                    passenger.remove();
                }
            }
            return;
        }
        var entity = plugin.getServer().getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 25L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 5L);
    }

    /**
     * TextDisplay passengers block Paper/Multiverse cross-world async teleports.
     * Drop the tag before teleport, then re-apply after it completes.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleportStart(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        removeDisplay(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportDone(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) {
            // We may have stripped the display at LOWEST; put it back.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    apply(player);
                }
            }, 1L);
            return;
        }
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        }, 2L);
    }

    private static Component mm(String mini) {
        return MM.deserialize(mini);
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
