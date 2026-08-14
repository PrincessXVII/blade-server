package dev.systemcrash.blade.cosmetics;

import dev.systemcrash.blade.BladeCorePlugin;
import dev.systemcrash.blade.OrbsManager;
import dev.systemcrash.blade.cosmetics.cage.CageCatalog;
import dev.systemcrash.blade.cosmetics.cage.CageService;
import dev.systemcrash.blade.cosmetics.data.CosmeticsRepository;
import dev.systemcrash.blade.cosmetics.equip.HatEquipmentService;
import dev.systemcrash.blade.cosmetics.equip.MaceEquipmentService;
import dev.systemcrash.blade.cosmetics.equip.SwordEquipmentService;
import dev.systemcrash.blade.cosmetics.gui.CosmeticsGuiListener;
import dev.systemcrash.blade.cosmetics.gui.CosmeticsGuiService;
import dev.systemcrash.blade.cosmetics.hat.HatCatalog;
import dev.systemcrash.blade.cosmetics.hat.HatService;
import dev.systemcrash.blade.cosmetics.hub.HubCosmeticsListener;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectCatalog;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectEquipmentService;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectPlaybackService;
import dev.systemcrash.blade.cosmetics.killeffect.KillEffectService;
import dev.systemcrash.blade.cosmetics.mace.MaceCatalog;
import dev.systemcrash.blade.cosmetics.mace.MaceService;
import dev.systemcrash.blade.cosmetics.sword.SwordCatalog;
import dev.systemcrash.blade.cosmetics.sword.SwordService;
import dev.systemcrash.blade.cosmetics.tag.TagCatalog;
import dev.systemcrash.blade.cosmetics.tag.TagEquipmentService;
import dev.systemcrash.blade.cosmetics.tag.TagService;
import dev.systemcrash.blade.cosmetics.title.TitleCatalog;
import dev.systemcrash.blade.cosmetics.title.TitleEquipmentService;
import dev.systemcrash.blade.cosmetics.title.TitleService;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorCatalog;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorEquipmentService;
import dev.systemcrash.blade.cosmetics.titlecolor.TitleColorService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BladeCosmeticsPlugin extends JavaPlugin {
    private CosmeticsRepository repository;
    private HatCatalog hatCatalog;
    private SwordCatalog swordCatalog;
    private MaceCatalog maceCatalog;
    private TitleCatalog titleCatalog;
    private TitleColorCatalog titleColorCatalog;
    private KillEffectCatalog killEffectCatalog;
    private TagCatalog tagCatalog;
    private CageCatalog cageCatalog;
    private HatService hatService;
    private SwordService swordService;
    private MaceService maceService;
    private TitleService titleService;
    private TitleColorService titleColorService;
    private KillEffectService killEffectService;
    private TagService tagService;
    private CageService cageService;
    private HatEquipmentService equipmentService;
    private SwordEquipmentService swordEquipmentService;
    private MaceEquipmentService maceEquipmentService;
    private TitleEquipmentService titleEquipmentService;
    private TitleColorEquipmentService titleColorEquipmentService;
    private KillEffectEquipmentService killEffectEquipmentService;
    private KillEffectPlaybackService killEffectPlaybackService;
    private TagEquipmentService tagEquipmentService;
    private CosmeticsGuiService guiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hatCatalog = HatCatalog.load(this);
        swordCatalog = SwordCatalog.load(this);
        maceCatalog = MaceCatalog.load(this);
        titleCatalog = TitleCatalog.load(this);
        titleColorCatalog = TitleColorCatalog.load(this);
        killEffectCatalog = KillEffectCatalog.load(this);
        tagCatalog = TagCatalog.load(this);
        cageCatalog = CageCatalog.load(this);
        repository = new CosmeticsRepository(this);
        repository.open();
        List<Integer> pricePool = getConfig().getIntegerList("orb-price-pool");
        List<Integer> titlePricePool = getConfig().getIntegerList("title-orb-price-pool");
        if (titlePricePool == null || titlePricePool.isEmpty()) {
            titlePricePool = pricePool;
        }
        List<Integer> titleColorPricePool = getConfig().getIntegerList("title-color-orb-price-pool");
        if (titleColorPricePool == null || titleColorPricePool.isEmpty()) {
            titleColorPricePool = pricePool;
        }
        List<Integer> killEffectPricePool = getConfig().getIntegerList("kill-effect-orb-price-pool");
        if (killEffectPricePool == null || killEffectPricePool.isEmpty()) {
            killEffectPricePool = pricePool;
        }
        List<Integer> tagPricePool = getConfig().getIntegerList("tag-orb-price-pool");
        if (tagPricePool == null || tagPricePool.isEmpty()) {
            tagPricePool = pricePool;
        }
        repository.seedHatPrices(hatCatalog, pricePool);
        repository.seedSwordPrices(swordCatalog, pricePool);
        repository.seedMacePrices(maceCatalog, pricePool);
        repository.seedTitlePrices(titleCatalog, titlePricePool);
        repository.seedTitleColorPrices(titleColorCatalog, titleColorPricePool);
        repository.seedKillEffectPrices(killEffectCatalog, killEffectPricePool);
        repository.seedTagPrices(tagCatalog, tagPricePool);

        OrbsManager orbs = resolveOrbs();
        equipmentService = new HatEquipmentService(this, hatCatalog, repository);
        swordEquipmentService = new SwordEquipmentService(this, swordCatalog, repository);
        maceEquipmentService = new MaceEquipmentService(this, maceCatalog, repository);
        titleEquipmentService = new TitleEquipmentService(this, titleCatalog, repository);
        titleColorEquipmentService = new TitleColorEquipmentService(this, titleColorCatalog, repository);
        killEffectEquipmentService = new KillEffectEquipmentService(this, killEffectCatalog, repository);
        killEffectPlaybackService = new KillEffectPlaybackService(this, killEffectCatalog, repository);
        tagEquipmentService = new TagEquipmentService(this, tagCatalog, repository);
        hatService = new HatService(this, hatCatalog, repository, orbs, equipmentService);
        swordService = new SwordService(this, swordCatalog, repository, orbs, swordEquipmentService);
        maceService = new MaceService(this, maceCatalog, repository, orbs, maceEquipmentService);
        titleService = new TitleService(this, titleCatalog, repository, orbs, titleEquipmentService);
        titleColorService = new TitleColorService(
                this, titleColorCatalog, repository, orbs, titleColorEquipmentService);
        killEffectService = new KillEffectService(
                this, killEffectCatalog, repository, orbs, killEffectEquipmentService);
        tagService = new TagService(this, tagCatalog, repository, orbs, tagEquipmentService);
        cageService = new CageService(this, cageCatalog, repository);
        guiService = new CosmeticsGuiService(
                this, hatCatalog, swordCatalog, maceCatalog, titleCatalog, titleColorCatalog, killEffectCatalog, tagCatalog,
                cageCatalog,
                repository, hatService, swordService, maceService, titleService, titleColorService, killEffectService, tagService,
                cageService);

        Bukkit.getPluginManager().registerEvents(new CosmeticsGuiListener(guiService), this);
        Bukkit.getPluginManager().registerEvents(new HubCosmeticsListener(this, guiService), this);
        Bukkit.getPluginManager().registerEvents(equipmentService, this);
        Bukkit.getPluginManager().registerEvents(swordEquipmentService, this);
        Bukkit.getPluginManager().registerEvents(maceEquipmentService, this);
        Bukkit.getPluginManager().registerEvents(titleEquipmentService, this);
        Bukkit.getPluginManager().registerEvents(titleColorEquipmentService, this);
        Bukkit.getPluginManager().registerEvents(killEffectEquipmentService, this);
        Bukkit.getPluginManager().registerEvents(killEffectPlaybackService, this);
        Bukkit.getPluginManager().registerEvents(tagEquipmentService, this);

        equipmentService.start();
        swordEquipmentService.start();
        maceEquipmentService.start();
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
                if (event.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                    return;
                }
                try {
                    repository.prefetchPlayer(event.getUniqueId());
                } catch (Exception ex) {
                    getLogger().warning("Cosmetics prelogin prefetch failed: " + ex.getMessage());
                }
            }

            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                Player player = event.getPlayer();
                java.util.UUID id = player.getUniqueId();
                // Apply from prefetched cache immediately; refresh from DB async if cold.
                if (equipmentService != null) {
                    equipmentService.applySoon(player);
                }
                if (swordEquipmentService != null) {
                    swordEquipmentService.apply(player);
                }
                if (maceEquipmentService != null) {
                    maceEquipmentService.apply(player);
                }
                if (titleEquipmentService != null) {
                    titleEquipmentService.apply(player);
                }
                if (tagEquipmentService != null) {
                    tagEquipmentService.apply(player);
                }
                Bukkit.getScheduler().runTaskAsynchronously(BladeCosmeticsPlugin.this, () -> {
                    try {
                        repository.prefetchPlayer(id);
                    } catch (Exception ex) {
                        getLogger().warning("Cosmetics reload failed: " + ex.getMessage());
                    }
                    Bukkit.getScheduler().runTask(BladeCosmeticsPlugin.this, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (equipmentService != null) {
                            equipmentService.apply(player);
                        }
                        if (swordEquipmentService != null) {
                            swordEquipmentService.apply(player);
                        }
                        if (maceEquipmentService != null) {
                            maceEquipmentService.apply(player);
                        }
                        if (titleEquipmentService != null) {
                            titleEquipmentService.apply(player);
                        }
                        if (tagEquipmentService != null) {
                            tagEquipmentService.apply(player);
                        }
                    });
                });
            }
        }, this);
        getLogger().info("BladeCosmetics enabled (" + hatCatalog.size() + " hats, "
                + swordCatalog.size() + " swords, " + maceCatalog.size() + " maces, "
                + titleCatalog.size() + " titles, "
                + titleColorCatalog.size() + " title colors, "
                + killEffectCatalog.size() + " kill effects, "
                + tagCatalog.size() + " tags, "
                + cageCatalog.size() + " cages"
                + (repository.usingMysql() ? ", MySQL sync" : ", local SQLite")
                + ").");
    }

    @Override
    public void onDisable() {
        if (equipmentService != null) {
            equipmentService.stop();
        }
        if (swordEquipmentService != null) {
            swordEquipmentService.stop();
        }
        if (maceEquipmentService != null) {
            maceEquipmentService.stop();
        }
        if (tagEquipmentService != null) {
            tagEquipmentService.stop();
        }
        if (repository != null) {
            repository.close();
        }
    }

    private OrbsManager resolveOrbs() {
        BladeCorePlugin core = (BladeCorePlugin) Bukkit.getPluginManager().getPlugin("BladeCore");
        if (core == null) {
            throw new IllegalStateException("BladeCore is required");
        }
        return core.getOrbsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        guiService.openMain(player);
        return true;
    }

    public CosmeticsGuiService guiService() {
        return guiService;
    }

    public CosmeticsRepository repository() {
        return repository;
    }

    public HatEquipmentService equipmentService() {
        return equipmentService;
    }

    public SwordEquipmentService swordEquipmentService() {
        return swordEquipmentService;
    }

    public MaceEquipmentService maceEquipmentService() {
        return maceEquipmentService;
    }

    public TitleEquipmentService titleEquipmentService() {
        return titleEquipmentService;
    }

    public TitleColorEquipmentService titleColorEquipmentService() {
        return titleColorEquipmentService;
    }

    public KillEffectEquipmentService killEffectEquipmentService() {
        return killEffectEquipmentService;
    }

    public TagEquipmentService tagEquipmentService() {
        return tagEquipmentService;
    }

    public TitleColorCatalog titleColorCatalog() {
        return titleColorCatalog;
    }

    public KillEffectCatalog killEffectCatalog() {
        return killEffectCatalog;
    }

    public TagCatalog tagCatalog() {
        return tagCatalog;
    }

    public HatService hatService() {
        return hatService;
    }

    public SwordService swordService() {
        return swordService;
    }

    public MaceService maceService() {
        return maceService;
    }

    public TitleService titleService() {
        return titleService;
    }

    public TitleColorService titleColorService() {
        return titleColorService;
    }

    public KillEffectService killEffectService() {
        return killEffectService;
    }

    public TagService tagService() {
        return tagService;
    }

    public CageService cageService() {
        return cageService;
    }

    public CageCatalog cageCatalog() {
        return cageCatalog;
    }

    /**
     * Meetups reads this via reflection when pasting a queue/match cage.
     * Empty = default glass cage.
     */
    public java.util.Optional<String> equippedMeetupCageSchematic(java.util.UUID uuid) {
        if (cageService == null) {
            return java.util.Optional.empty();
        }
        return cageService.equippedSchematic(uuid);
    }

    /** Called from ServerSpawnControl via reflection when arriving at lobby. */
    public void giveHubItem(Player player) {
        int slot = getConfig().getInt("hub-slot", 1);
        player.getInventory().setItem(slot, HubCosmeticsListener.createHubItem(this));
    }
}
