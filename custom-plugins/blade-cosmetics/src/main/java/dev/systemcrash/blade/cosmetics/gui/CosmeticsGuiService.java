package dev.systemcrash.blade.cosmetics.gui;

import dev.systemcrash.blade.cosmetics.BladeCosmeticsPlugin;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.equip.HatEquipmentService;
import dev.systemcrash.blade.cosmetics.equip.MaceEquipmentService;
import dev.systemcrash.blade.cosmetics.equip.SwordEquipmentService;
import dev.systemcrash.blade.cosmetics.hat.HatCatalog;
import dev.systemcrash.blade.cosmetics.hat.HatDefinition;
import dev.systemcrash.blade.cosmetics.hat.HatService;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectCatalog;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectDefinition;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectEquipmentService;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectService;
import dev.systemcrash.blade.cosmetics.mace.MaceCatalog;
import dev.systemcrash.blade.cosmetics.mace.MaceDefinition;
import dev.systemcrash.blade.cosmetics.mace.MaceService;
import dev.systemcrash.blade.cosmetics.sword.SwordCatalog;
import dev.systemcrash.blade.cosmetics.sword.SwordDefinition;
import dev.systemcrash.blade.cosmetics.sword.SwordService;
import dev.systemcrash.blade.cosmetics.cage.CageCatalog;
import dev.systemcrash.blade.cosmetics.cage.CageDefinition;
import dev.systemcrash.blade.cosmetics.cage.CageService;
import dev.systemcrash.blade.cosmetics.tag.TagCatalog;
import dev.systemcrash.blade.cosmetics.tag.TagDefinition;
import dev.systemcrash.blade.cosmetics.tag.TagEquipmentService;
import dev.systemcrash.blade.cosmetics.tag.TagService;
import dev.systemcrash.blade.cosmetics.title.TitleCatalog;
import dev.systemcrash.blade.cosmetics.title.TitleDefinition;
import dev.systemcrash.blade.cosmetics.title.TitleEquipmentService;
import dev.systemcrash.blade.cosmetics.title.TitleService;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorCatalog;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorDefinition;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorEquipmentService;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CosmeticsGuiService {
    public static final int HATS_PER_PAGE = 21;
    public static final int SWORDS_PER_PAGE = 21;
    public static final int MACES_PER_PAGE = 21;
    public static final int TITLES_PER_PAGE = 21;
    public static final int TITLE_COLORS_PER_PAGE = 21;
    public static final int KILL_EFFECTS_PER_PAGE = 21;
    public static final int TAGS_PER_PAGE = 21;
    public static final int CAGES_PER_PAGE = 21;
    /** Columns 2-8 on rows 2,3,4 (0-based slots). */
    public static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    public static final int SLOT_BACK = 0;
    public static final int SLOT_CLEAR = 4;
    public static final int SLOT_PREV = 18;
    public static final int SLOT_NEXT = 26;
    public static final int SLOT_CATEGORY_HATS = 2;
    public static final int SLOT_CATEGORY_SWORDS = 3;
    public static final int SLOT_CATEGORY_TITLES = 4;
    public static final int SLOT_CATEGORY_TITLE_COLORS = 5;
    public static final int SLOT_CATEGORY_KILL_EFFECTS = 6;
    /** Directly under hats (slot 2 → row below). */
    public static final int SLOT_CATEGORY_MACES = 11;
    /** Unused — tags removed from main menu. */
    public static final int SLOT_CATEGORY_TAGS = 11;
    /** Right of tags. */
    public static final int SLOT_CATEGORY_CAGES = 12;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** White (&f) before glyph — otherwise bitmap GUI tints black. */
    private static final Component MAIN_TITLE = Component.text(
            "\uF808\uF806\uF826\uE201", NamedTextColor.WHITE);
    private static final Component HATS_TITLE = Component.text(
            "\uF808\uF806\uF826\uE202", NamedTextColor.WHITE);
    private static final Component SWORDS_TITLE = Component.text(
            "\uF808\uF806\uF826\uE203", NamedTextColor.WHITE);
    private static final Component MACES_TITLE = Component.text(
            "\uF808\uF806\uF826\uE203", NamedTextColor.WHITE);
    private static final Component TITLES_TITLE = Component.text(
            "\uF808\uF806\uF826\uE204", NamedTextColor.WHITE);
    private static final Component TITLE_COLORS_TITLE = Component.text(
            "\uF808\uF806\uF826\uE205", NamedTextColor.WHITE);
    private static final Component KILL_EFFECTS_TITLE = Component.text(
            "\uF808\uF806\uF826\uE206", NamedTextColor.WHITE);
    /** Same cosmetics GUI chrome as other categories. */
    private static final Component TAGS_TITLE = Component.text(
            "\uF808\uF806\uF826\uE204", NamedTextColor.WHITE);
    private static final Component CAGES_TITLE = Component.text(
            "\uF808\uF806\uF826\uE204", NamedTextColor.WHITE);

    private final BladeCosmeticsPlugin plugin;
    private final HatCatalog hatCatalog;
    private final SwordCatalog swordCatalog;
    private final MaceCatalog maceCatalog;
    private final TitleCatalog titleCatalog;
    private final TitleColorCatalog titleColorCatalog;
    private final KillEffectCatalog killEffectCatalog;
    private final TagCatalog tagCatalog;
    private final CageCatalog cageCatalog;
    private final CosmeticsRepository repository;
    private final HatService hatService;
    private final SwordService swordService;
    private final MaceService maceService;
    private final TitleService titleService;
    private final TitleColorService titleColorService;
    private final KillEffectService killEffectService;
    private final TagService tagService;
    private final CageService cageService;

    public CosmeticsGuiService(
            BladeCosmeticsPlugin plugin,
            HatCatalog hatCatalog,
            SwordCatalog swordCatalog,
            MaceCatalog maceCatalog,
            TitleCatalog titleCatalog,
            TitleColorCatalog titleColorCatalog,
            KillEffectCatalog killEffectCatalog,
            TagCatalog tagCatalog,
            CageCatalog cageCatalog,
            CosmeticsRepository repository,
            HatService hatService,
            SwordService swordService,
            MaceService maceService,
            TitleService titleService,
            TitleColorService titleColorService,
            KillEffectService killEffectService,
            TagService tagService,
            CageService cageService
    ) {
        this.plugin = plugin;
        this.hatCatalog = hatCatalog;
        this.swordCatalog = swordCatalog;
        this.maceCatalog = maceCatalog;
        this.titleCatalog = titleCatalog;
        this.titleColorCatalog = titleColorCatalog;
        this.killEffectCatalog = killEffectCatalog;
        this.tagCatalog = tagCatalog;
        this.cageCatalog = cageCatalog;
        this.repository = repository;
        this.hatService = hatService;
        this.swordService = swordService;
        this.maceService = maceService;
        this.titleService = titleService;
        this.titleColorService = titleColorService;
        this.killEffectService = killEffectService;
        this.tagService = tagService;
        this.cageService = cageService;
    }

    public void openMain(Player player) {
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.MAIN, 0);
        Inventory inv = Bukkit.createInventory(holder, 18, MAIN_TITLE);
        holder.bind(inv);
        inv.setItem(SLOT_CATEGORY_HATS, categoryHatsItem());
        inv.setItem(SLOT_CATEGORY_SWORDS, categorySwordsItem());
        inv.setItem(SLOT_CATEGORY_TITLES, categoryTitlesItem());
        inv.setItem(SLOT_CATEGORY_TITLE_COLORS, categoryTitleColorsItem());
        inv.setItem(SLOT_CATEGORY_MACES, categoryMacesItem());
        // Kill effects removed from cosmetics menu; cages take that slot.
        if (cageService.hasCageCategory(player)) {
            inv.setItem(SLOT_CATEGORY_KILL_EFFECTS, categoryCagesItem());
        }
        player.openInventory(inv);
    }

    public void openHats(Player player, int page) {
        int pages = pricedPageCount(hatCatalog.size(), HATS_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.HATS, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, HATS_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedHats(player.getUniqueId());
        HatEquipmentService equipment = plugin.equipmentService();
        List<HatDefinition> hats = hatsPage(safePage);
        for (int i = 0; i < hats.size() && i < ITEM_SLOTS.length; i++) {
            HatDefinition hat = hats.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(hat, owned.contains(hat.id()), repository.priceOf(hat.id())));
        }
        player.openInventory(inv);
    }

    public void openSwords(Player player, int page) {
        int pages = pricedPageCount(swordCatalog.size(), SWORDS_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.SWORDS, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, SWORDS_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedSwords(player.getUniqueId());
        SwordEquipmentService equipment = plugin.swordEquipmentService();
        List<SwordDefinition> swords = swordsPage(safePage);
        for (int i = 0; i < swords.size() && i < ITEM_SLOTS.length; i++) {
            SwordDefinition sword = swords.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    sword, owned.contains(sword.id()), repository.swordPriceOf(sword.id())));
        }
        player.openInventory(inv);
    }

    public void openMaces(Player player, int page) {
        int pages = pricedPageCount(maceCatalog.size(), MACES_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.MACES, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, MACES_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedMaces(player.getUniqueId());
        MaceEquipmentService equipment = plugin.maceEquipmentService();
        List<MaceDefinition> maces = macesPage(safePage);
        for (int i = 0; i < maces.size() && i < ITEM_SLOTS.length; i++) {
            MaceDefinition mace = maces.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    mace, owned.contains(mace.id()), repository.macePriceOf(mace.id())));
        }
        player.openInventory(inv);
    }

    public void openTitles(Player player, int page) {
        int pages = pricedPageCount(titleCatalog.size(), TITLES_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.TITLES, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, TITLES_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedTitles(player.getUniqueId());
        TitleEquipmentService equipment = plugin.titleEquipmentService();
        List<TitleDefinition> titles = titlesPage(safePage);
        for (int i = 0; i < titles.size() && i < ITEM_SLOTS.length; i++) {
            TitleDefinition title = titles.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    title, owned.contains(title.id()), repository.titlePriceOf(title.id())));
        }
        player.openInventory(inv);
    }

    public void openTitleColors(Player player, int page) {
        int pages = pricedPageCount(titleColorCatalog.size(), TITLE_COLORS_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.TITLE_COLORS, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, TITLE_COLORS_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedTitleColors(player.getUniqueId());
        TitleColorEquipmentService equipment = plugin.titleColorEquipmentService();
        List<TitleColorDefinition> colors = titleColorsPage(safePage);
        for (int i = 0; i < colors.size() && i < ITEM_SLOTS.length; i++) {
            TitleColorDefinition color = colors.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    color, owned.contains(color.id()), repository.titleColorPriceOf(color.id())));
        }
        player.openInventory(inv);
    }

    public void openKillEffects(Player player, int page) {
        int pages = pricedPageCount(killEffectCatalog.size(), KILL_EFFECTS_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.KILL_EFFECTS, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, KILL_EFFECTS_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedKillEffects(player.getUniqueId());
        KillEffectEquipmentService equipment = plugin.killEffectEquipmentService();
        List<KillEffectDefinition> effects = killEffectsPage(safePage);
        for (int i = 0; i < effects.size() && i < ITEM_SLOTS.length; i++) {
            KillEffectDefinition effect = effects.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    effect, owned.contains(effect.id()), repository.killEffectPriceOf(effect.id())));
        }
        player.openInventory(inv);
    }

    public void openTags(Player player, int page) {
        int pages = pricedPageCount(tagCatalog.size(), TAGS_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.TAGS, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, TAGS_TITLE);
        holder.bind(inv);
        placeNav(inv);
        Set<String> owned = repository.ownedTags(player.getUniqueId());
        TagEquipmentService equipment = plugin.tagEquipmentService();
        List<TagDefinition> tags = tagsPage(safePage);
        for (int i = 0; i < tags.size() && i < ITEM_SLOTS.length; i++) {
            TagDefinition tag = tags.get(i);
            inv.setItem(ITEM_SLOTS[i], equipment.createGuiIcon(
                    tag, owned.contains(tag.id()), repository.tagPriceOf(tag.id())));
        }
        player.openInventory(inv);
    }

    public void openCages(Player player, int page) {
        List<CageDefinition> available = cageService.availableCages(player);
        if (available.isEmpty()) {
            openMain(player);
            return;
        }
        int pages = Math.max(1, (available.size() + CAGES_PER_PAGE - 1) / CAGES_PER_PAGE);
        int safePage = Math.floorMod(page, pages);
        CosmeticsMenuHolder holder = new CosmeticsMenuHolder(CosmeticsMenuHolder.Type.CAGES, safePage);
        Inventory inv = Bukkit.createInventory(holder, 45, CAGES_TITLE);
        holder.bind(inv);
        placeNav(inv);
        int from = safePage * CAGES_PER_PAGE;
        List<CageDefinition> cages = available.subList(from, Math.min(available.size(), from + CAGES_PER_PAGE));
        for (int i = 0; i < cages.size() && i < ITEM_SLOTS.length; i++) {
            inv.setItem(ITEM_SLOTS[i], cageService.createGuiIcon(cages.get(i), player));
        }
        player.openInventory(inv);
    }

    private void placeNav(Inventory inv) {
        inv.setItem(SLOT_BACK, blankButton(
                "<!italic><color:#FFFFFF>Bᴇᴘʜутьᴄя</color>",
                List.of(MM.deserialize("<!italic><color:#FFFFFF>ʜᴀжми, чтᴏбы ʙᴇᴘʜутьᴄя</color>"))
        ));
        inv.setItem(SLOT_CLEAR, blankButton(
                "<!italic><color:#FFFFFF>ᴏчиᴄтить</color>",
                List.of(MM.deserialize("<!italic><color:#AAAAAA>ʜᴀжми, чтᴏбы ᴏчиᴄтить эту ᴋᴏᴄмᴇтиᴋу</color>"))
        ));
        inv.setItem(SLOT_PREV, navButton(true));
        inv.setItem(SLOT_NEXT, navButton(false));
    }

    public void handleClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (holder.type() == CosmeticsMenuHolder.Type.MAIN) {
            if (rawSlot == SLOT_CATEGORY_HATS) {
                openHats(player, 0);
            } else if (rawSlot == SLOT_CATEGORY_SWORDS) {
                openSwords(player, 0);
            } else if (rawSlot == SLOT_CATEGORY_TITLES) {
                openTitles(player, 0);
            } else if (rawSlot == SLOT_CATEGORY_TITLE_COLORS) {
                openTitleColors(player, 0);
            } else if (rawSlot == SLOT_CATEGORY_MACES) {
                openMaces(player, 0);
            } else if (rawSlot == SLOT_CATEGORY_KILL_EFFECTS) {
                // Slot reused for meetups cages (kill effects removed from menu).
                if (cageService.hasCageCategory(player)) {
                    openCages(player, 0);
                }
            } else if (rawSlot == SLOT_CATEGORY_CAGES) {
                if (cageService.hasCageCategory(player)) {
                    openCages(player, 0);
                }
            }
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.HATS) {
            handleHatsClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.SWORDS) {
            handleSwordsClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.MACES) {
            handleMacesClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.TITLES) {
            handleTitlesClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.TITLE_COLORS) {
            handleTitleColorsClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.KILL_EFFECTS) {
            handleKillEffectsClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.TAGS) {
            handleTagsClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == CosmeticsMenuHolder.Type.CAGES) {
            handleCagesClick(player, holder, rawSlot);
        }
    }

    private void handleHatsClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            hatService.clearHat(player);
            openHats(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openHats(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openHats(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<HatDefinition> hats = hatsPage(holder.page());
            if (i >= hats.size()) {
                return;
            }
            hatService.clickHat(player, hats.get(i).id());
            openHats(player, holder.page());
            return;
        }
    }

    private void handleSwordsClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            swordService.clearSword(player);
            openSwords(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openSwords(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openSwords(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<SwordDefinition> swords = swordsPage(holder.page());
            if (i >= swords.size()) {
                return;
            }
            swordService.clickSword(player, swords.get(i).id());
            openSwords(player, holder.page());
            return;
        }
    }

    private void handleMacesClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            maceService.clearMace(player);
            openMaces(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openMaces(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openMaces(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<MaceDefinition> maces = macesPage(holder.page());
            if (i >= maces.size()) {
                return;
            }
            maceService.clickMace(player, maces.get(i).id());
            openMaces(player, holder.page());
            return;
        }
    }

    private void handleTitlesClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            titleService.clearTitle(player);
            openTitles(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openTitles(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openTitles(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<TitleDefinition> titles = titlesPage(holder.page());
            if (i >= titles.size()) {
                return;
            }
            titleService.clickTitle(player, titles.get(i).id());
            openTitles(player, holder.page());
            return;
        }
    }

    private void handleTitleColorsClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            titleColorService.clearColor(player);
            openTitleColors(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openTitleColors(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openTitleColors(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<TitleColorDefinition> colors = titleColorsPage(holder.page());
            if (i >= colors.size()) {
                return;
            }
            titleColorService.clickColor(player, colors.get(i).id());
            openTitleColors(player, holder.page());
            return;
        }
    }

    private void handleKillEffectsClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            killEffectService.clearEffect(player);
            openKillEffects(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openKillEffects(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openKillEffects(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<KillEffectDefinition> effects = killEffectsPage(holder.page());
            if (i >= effects.size()) {
                return;
            }
            killEffectService.clickEffect(player, effects.get(i).id());
            openKillEffects(player, holder.page());
            return;
        }
    }

    private void handleTagsClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            tagService.clearTag(player);
            openTags(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openTags(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openTags(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<TagDefinition> tags = tagsPage(holder.page());
            if (i >= tags.size()) {
                return;
            }
            tagService.clickTag(player, tags.get(i).id());
            openTags(player, holder.page());
            return;
        }
    }

    private void handleCagesClick(Player player, CosmeticsMenuHolder holder, int rawSlot) {
        if (rawSlot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (rawSlot == SLOT_CLEAR) {
            cageService.clearCage(player);
            openCages(player, holder.page());
            return;
        }
        if (rawSlot == SLOT_PREV) {
            openCages(player, holder.page() - 1);
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            openCages(player, holder.page() + 1);
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] != rawSlot) {
                continue;
            }
            List<CageDefinition> available = cageService.availableCages(player);
            int from = holder.page() * CAGES_PER_PAGE;
            if (from >= available.size() || i >= CAGES_PER_PAGE) {
                return;
            }
            int index = from + i;
            if (index >= available.size()) {
                return;
            }
            cageService.clickCage(player, available.get(index).id());
            openCages(player, holder.page());
            return;
        }
    }

    private List<HatDefinition> hatsPage(int page) {
        return pageByPriceDesc(hatCatalog.all(), HatDefinition::id, repository::priceOf, page, HATS_PER_PAGE);
    }

    private List<SwordDefinition> swordsPage(int page) {
        return pageByPriceDesc(swordCatalog.all(), SwordDefinition::id, repository::swordPriceOf, page, SWORDS_PER_PAGE);
    }

    private List<MaceDefinition> macesPage(int page) {
        return pageByPriceDesc(maceCatalog.all(), MaceDefinition::id, repository::macePriceOf, page, MACES_PER_PAGE);
    }

    private List<TitleDefinition> titlesPage(int page) {
        return pageByPriceDesc(titleCatalog.all(), TitleDefinition::id, repository::titlePriceOf, page, TITLES_PER_PAGE);
    }

    private List<TitleColorDefinition> titleColorsPage(int page) {
        return pageByPriceDesc(
                titleColorCatalog.all(), TitleColorDefinition::id, repository::titleColorPriceOf, page, TITLE_COLORS_PER_PAGE);
    }

    private List<KillEffectDefinition> killEffectsPage(int page) {
        return pageByPriceDesc(
                killEffectCatalog.all(), KillEffectDefinition::id, repository::killEffectPriceOf, page, KILL_EFFECTS_PER_PAGE);
    }

    private List<TagDefinition> tagsPage(int page) {
        return pageByPriceDesc(tagCatalog.all(), TagDefinition::id, repository::tagPriceOf, page, TAGS_PER_PAGE);
    }

    private static int pricedPageCount(int size, int pageSize) {
        if (pageSize <= 0 || size <= 0) {
            return 1;
        }
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private static <T> List<T> pageByPriceDesc(
            List<T> all,
            Function<T, String> idFn,
            ToIntFunction<String> priceFn,
            int page,
            int pageSize
    ) {
        List<T> sorted = new ArrayList<>(all);
        sorted.sort(Comparator
                .comparingInt((T t) -> priceFn.applyAsInt(idFn.apply(t)))
                .reversed()
                .thenComparing(t -> idFn.apply(t)));
        int from = page * pageSize;
        if (from >= sorted.size()) {
            return List.of();
        }
        return sorted.subList(from, Math.min(sorted.size(), from + pageSize));
    }

    private ItemStack categoryHatsItem() {
        int cmd = plugin.getConfig().getInt("items.category-hats", 7003);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>шляпы</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categorySwordsItem() {
        int cmd = plugin.getConfig().getInt("items.category-swords", 7007);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>мечи</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryMacesItem() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>булавы</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryTitlesItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>титулы</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryTitleColorsItem() {
        ItemStack item = new ItemStack(Material.CYAN_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>цвета титулов</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryKillEffectsItem() {
        int cmd = plugin.getConfig().getInt("items.category-kill-effects", 7008);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>эффекты убийства</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryTagsItem() {
        ItemStack item = new ItemStack(Material.WIND_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>теги</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize(
                "<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>"
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryCagesItem() {
        ItemStack item = new ItemStack(Material.TRIAL_SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(
                "<!italic><color:#AAAAAA>❏ ᴋᴏᴄмᴇтиᴋᴀ: </color>"
                        + "<gradient:#FF00C8:#DB00FF:#ff00c8>клетки митапов</gradient>"
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MM.deserialize("<!italic><color:#AAAAAA>кастомные клетки для очереди</color>"),
                MM.deserialize("<!italic><color:#5FE2C5>ʜᴀжми</color><color:#AAAAAA>, чтобы открыть</color>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack blankButton(String nameMm, List<Component> lore) {
        int cmd = plugin.getConfig().getInt("items.blank", 7006);
        return button(Material.PAPER, cmd, nameMm, lore);
    }

    private ItemStack navButton(boolean prev) {
        int cmd = plugin.getConfig().getInt(prev ? "items.prev-page" : "items.next-page", prev ? 7004 : 7005);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(Component.space().decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "ui"),
                PersistentDataType.BYTE,
                (byte) 1
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, int cmd, String nameMm, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        meta.displayName(MM.deserialize(nameMm).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "ui"),
                PersistentDataType.BYTE,
                (byte) 1
        );
        item.setItemMeta(meta);
        return item;
    }
}
