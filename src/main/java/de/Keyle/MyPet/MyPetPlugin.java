/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2020 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet;

import de.Keyle.MyPet.api.*;
import de.Keyle.MyPet.api.entity.EntityRegistry;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetInfo;
import de.Keyle.MyPet.api.entity.StoredMyPet;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagManager;
import de.Keyle.MyPet.api.player.ContributorCheck;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.repository.*;
import de.Keyle.MyPet.api.skill.SkillManager;
import de.Keyle.MyPet.api.skill.skilltree.Skill;
import de.Keyle.MyPet.api.skill.experience.ExperienceCache;
import de.Keyle.MyPet.api.skill.experience.ExperienceCalculatorManager;
import de.Keyle.MyPet.api.skill.skilltree.SkillTreeLoaderJSON;
import de.Keyle.MyPet.api.skill.skilltree.SkilltreeManager;
import de.Keyle.MyPet.api.util.*;
import de.Keyle.MyPet.api.util.hooks.HookHelper;
import de.Keyle.MyPet.api.util.hooks.PluginHook;
import de.Keyle.MyPet.api.util.hooks.PluginHookManager;
import de.Keyle.MyPet.api.util.inventory.material.ItemDatabase;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.logger.DebugLogHandler;
import de.Keyle.MyPet.api.util.service.Load;
import de.Keyle.MyPet.api.util.service.ServiceManager;
import de.Keyle.MyPet.commands.*;
import de.Keyle.MyPet.entity.leashing.*;
import de.Keyle.MyPet.listeners.*;
import de.Keyle.MyPet.repository.Converter;
import de.Keyle.MyPet.repository.types.MongoDBRepository;
import de.Keyle.MyPet.repository.types.MySQLRepository;
import de.Keyle.MyPet.repository.types.SQLiteRepository;
import de.Keyle.MyPet.services.RepositoryMyPetConverterService;
import de.Keyle.MyPet.skill.experience.JavaScriptExperienceCalculator;
import de.Keyle.MyPet.skill.skills.*;
import de.Keyle.MyPet.skill.skilltree.requirements.NoSkilltreeRequirement;
import de.Keyle.MyPet.skill.skilltree.requirements.PermissionRequirement;
import de.Keyle.MyPet.skill.skilltree.requirements.PetLevelRequirement;
import de.Keyle.MyPet.skill.skilltree.requirements.SkilltreeRequirement;
import de.Keyle.MyPet.util.ConfigurationLoader;
import de.Keyle.MyPet.util.Updater;
import de.Keyle.MyPet.util.hooks.*;
import de.Keyle.MyPet.util.player.MyPetPlayerImpl;
import de.Keyle.MyPet.util.shop.ShopManager;
import de.Keyle.MyPet.util.translation.VanillaTranslationLoader;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.time.Year;
import java.util.UUID;


@SuppressWarnings("unused")
public final class MyPetPlugin extends JavaPlugin implements de.Keyle.MyPet.api.plugin.MyPetPlugin {

    private boolean isReady = false;
    private boolean isDisabling = false;
    private Repository repo;
    private MyPetInfo petInfo;
    private PlatformHelper platformHelper;
    private EntityRegistry entityRegistry;
    private CompatUtil compatUtil;
    private CompatManager compatManager;
    private PlayerManager playerManager;
    private MyPetManager myPetManager;
    private HookHelper hookHelper;
    private PluginHookManager pluginHookManager;
    private ServiceManager serviceManager;

    public void onDisable() {
        isDisabling = true;

        // 中断任何正在进行的原版翻译下载，以免在插件实例销毁后
        // 仍向共享静态字段写入过期的翻译映射。
        VanillaTranslationLoader.cancelLoad();

        if (isReady) {
            for (MyPet myPet : myPetManager.getAllActiveMyPets()) {
                if (myPet.getStatus() == MyPet.PetState.Here) {
                    myPet.removePet(true);
                }
            }
            repo.disable();
            entityRegistry.unregisterEntityTypes();
            Timer.reset();
        }
        Bukkit.getServer().getScheduler().cancelTasks(this);

        DebugLogHandler.disable(getLogger());

        if (pluginHookManager != null) {
            pluginHookManager.disableHooks();
        }
        if (serviceManager != null) {
            serviceManager.disableServices();
        }
    }

    public void onLoad() {
        MyPetApi.setPlugin(this);
        getDataFolder().mkdirs();

        getLogger().setFilter(record -> {
            record.setMessage(Colorizer.toAnsi(record.getMessage()));
            return true;
        });

        // 从 manifest 中加载版本号
        MyPetVersion.reset();

        compatUtil = new CompatUtil();

        ConfigurationLoader.upgradeConfig();
        ConfigurationLoader.setDefault();
        ConfigurationLoader.loadConfiguration();

        serviceManager = new ServiceManager();
        pluginHookManager = new PluginHookManager();

        if (compatUtil.getInternalVersion() == null || !MyPetVersion.isValidBukkitPacket(compatUtil.getInternalVersion())) {
            return;
        }

        petInfo = compatUtil.getCompatInstance(MyPetInfo.class, "entity", "MyPetInfo");
        platformHelper = compatUtil.getCompatInstance(PlatformHelper.class, "", "PlatformHelper");
        entityRegistry = compatUtil.getCompatInstance(EntityRegistry.class, "entity", "EntityRegistry");
        myPetManager = new de.Keyle.MyPet.repository.MyPetManager();
        playerManager = new de.Keyle.MyPet.repository.PlayerManager();
        hookHelper = new de.Keyle.MyPet.util.HookHelper();

        registerServices();

        compatManager = compatUtil.getCompatInstance(CompatManager.class, "", "CompatManager");
        if (petInfo == null || platformHelper == null || entityRegistry == null || compatManager == null) {
            // 该服务器上无法加载某个兼容类（getCompatInstance 已经记录了是哪个类）。
            // onEnable 会带着说明禁用 MyPet，因此在此停止，而不是因为缺失的部分崩溃
            // （MYPET-E84、MYPET-D57）。
            return;
        }
        compatManager.init();

        serviceManager.activate(Load.State.OnLoad);

        registerHooks();
    }

    /**
     * 检查服务器运行的 Minecraft 版本是否比最后受支持的版本更新。
     *
     * @return 如果服务器版本比 26.3 更新且没有受支持的兼容模块，则返回 true
     */
    private boolean isNewerThanMyPet3() {
        String minecraftVersion = compatUtil.getMinecraftVersion();
        boolean unsupported = compatUtil.getInternalVersion() == null || !MyPetVersion.isValidBukkitPacket(compatUtil.getInternalVersion());
        return unsupported && minecraftVersion != null && !minecraftVersion.equals("0.0.0")
                && compatUtil.compareWithMinecraftVersion("26.3") >= 0;
    }

    public void onEnable() {
        this.isReady = false;

        // MyPet 3 止于 Minecraft 26.3。在更新的版本上，请在初始化插件之前停止。
        if (isNewerThanMyPet3()) {
            String minecraftVersion = compatUtil.getMinecraftVersion();
            getLogger().warning("This version of MyPet is not compatible with Minecraft " + minecraftVersion + ".");
            setEnabled(false);
            return;
        }

        Updater updater = new Updater("MyPet");
        printSplashScreen(Configuration.Update.CHECK ? "Checking for updates..." : null);

        // 更新检查可能因网络而阻塞，因此在后台线程中运行。
        Thread startupChecks = new Thread(() -> {
            try {
                String updateStatus = updater.update();
                if (updateStatus != null) {
                    getLogger().info(updateStatus);
                }
            } catch (Throwable t) {
                // 检查仍在运行时 MyPet 可能已被禁用或重新加载
                getLogger().fine("Update check stopped: " + t);
            }
        }, "MyPet Update Check");
        // 不是守护线程：从这里启动的更新下载必须能在 JVM 退出前写完文件，
        // 这与检查移到主线程之外之前的行为一致。
        startupChecks.setDaemon(false);
        startupChecks.start();

        if (compatUtil.getInternalVersion() == null || !MyPetVersion.isValidBukkitPacket(compatUtil.getInternalVersion())) {
            getLogger().warning("This version of MyPet is not compatible with \"" + compatUtil.getInternalVersion() + " on " + compatUtil.getMinecraftVersion() + "\". Is MyPet up to date?");
            // MyPet 无法在此运行，因此给更新检查一个获取兼容版本的机会
            try {
                startupChecks.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            updater.waitForDownload();
            setEnabled(false);
            return;
        }

        // 在全新的服务端构建上，isValidBukkitPacket() 可能通过，但实际并未加载任何兼容模块，
        // 导致这些字段为 null（在 onLoad 中通过 getCompatInstance 设置）。请干净地退出，
        // 而不是在下面的 registerEntityTypes() / compatManager.enable() 中抛出 NPE。
        if (entityRegistry == null || platformHelper == null || petInfo == null || compatManager == null) {
            getLogger().severe("No MyPet compatibility module could be loaded for " + compatUtil.getMinecraftVersion() + " (internal version " + compatUtil.getInternalVersion() + "). MyPet cannot run on this server version - disabling.");
            setEnabled(false);
            return;
        }

        serviceManager.activate(Load.State.OnEnable);

        try {
            entityRegistry.registerEntityTypes();
        } catch (LinkageError e) {
            // 实体注册的 hack 会反射访问原版/Spigot 注册表的内部实现。在
            // Forge-Bukkit 混合端（Arclight、Mohist 等）上，这些内部实现被重映射，因此
            // 字段/方法访问会抛出 NoSuchFieldError/NoSuchMethodError 等（LinkageError）。
            // 这些平台不受支持——请干净地禁用，而不是半启用。
            getLogger().severe("Failed to register MyPet's entities on this server (" + compatUtil.getMinecraftVersion() + "). " +
                    "This usually means an unsupported server platform - MyPet does not support Forge-Bukkit hybrids such as Arclight or Mohist. Disabling.");
            getLogger().severe("Reason: " + e);
            setEnabled(false);
            return;
        }

        DebugLogHandler.setup(getLogger());

        compatManager.enable();

        // 从官方资源 CDN 获取 Mojang 的原版翻译，以便在 MyPet 自身的语言文件中
        // 没有对应条目时，为新创建的宠物提供真正本地化的默认名称，
        // 而不是直接显示 "Name.HappyGhast" 这样的键名。
        VanillaTranslationLoader.loadAsync(this);

        ConfigurationLoader.loadCompatConfiguration();

        // 注册捕捉标志
        registerLeashFlags();

        // 注册技能树需求设置
        registerSkilltreeRequirements();

        // 注册经验计算器
        if (!new File(getDataFolder(), "exp.js").exists()) {
            platformHelper.copyResource(this, "exp.js", new File(getDataFolder(), "exp.js"));
        }
        serviceManager.getService(ExperienceCalculatorManager.class).ifPresent(calculatorManager -> {
            calculatorManager.registerCalculator("JS", JavaScriptExperienceCalculator.class);
            calculatorManager.registerCalculator("JavaScript", JavaScriptExperienceCalculator.class);
            calculatorManager.switchCalculator(Configuration.LevelSystem.CALCULATION_MODE.toLowerCase());
        });

        // 注册事件监听器
        PlayerListener playerListener = new PlayerListener();
        getServer().getPluginManager().registerEvents(playerListener, this);
        VehicleListener vehicleListener = new VehicleListener();
        getServer().getPluginManager().registerEvents(vehicleListener, this);
        EntityListener entityListener = new EntityListener();
        getServer().getPluginManager().registerEvents(entityListener, this);
        MyPetEntityListener myPetEntityListener = new MyPetEntityListener();
        getServer().getPluginManager().registerEvents(myPetEntityListener, this);
        LevelListener levelupListener = new LevelListener();
        getServer().getPluginManager().registerEvents(levelupListener, this);
        WorldListener worldListener = new WorldListener();
        getServer().getPluginManager().registerEvents(worldListener, this);
        // 为 1.21.4+ 注册 CreakingHeartListener（该版本加入 Creaking Heart 之时）
        if (MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.21.4") >= 0) {
            CreakingHeartListener creakingHeartListener = new CreakingHeartListener();
            getServer().getPluginManager().registerEvents(creakingHeartListener, this);
        }

        // 注册命令
        getCommand("petname").setExecutor(new CommandName());
        getCommand("petcall").setExecutor(new CommandCall());
        getCommand("petsendaway").setExecutor(new CommandSendAway());
        getCommand("petstop").setExecutor(new CommandStop());
        getCommand("petrelease").setExecutor(new CommandRelease());
        getCommand("mypet").setExecutor(new CommandMyPet());
        getCommand("petinventory").setExecutor(new CommandInventory());
        getCommand("petpickup").setExecutor(new CommandPickup());
        getCommand("petbehavior").setExecutor(new CommandBehavior());
        getCommand("petinfo").setExecutor(new CommandInfo());
        getCommand("mypetadmin").setExecutor(new CommandAdmin());
        getCommand("petskill").setExecutor(new CommandSkill());
        getCommand("petchooseskilltree").setExecutor(new CommandChooseSkilltree());
        getCommand("petbeacon").setExecutor(new CommandBeacon());
        getCommand("petrespawn").setExecutor(new CommandRespawn());
        getCommand("petsettings").setExecutor(new CommandSettings());
        getCommand("petswitch").setExecutor(new CommandSwitch());
        getCommand("petstore").setExecutor(new CommandStore());
        getCommand("petlist").setExecutor(new CommandList());
        getCommand("petcapturehelper").setExecutor(new CommandCaptureHelper());
        getCommand("pettrade").setExecutor(new CommandTrade());
        getCommand("petshop").setExecutor(new CommandShop());

        // 加载世界组
        WorldGroup.loadGroups(new File(getDataFolder().getPath(), "worldgroups.yml"));

        // 注册技能
        registerSkills();

        // 创建文件夹
        File skilltreeFolder = new File(getDataFolder().getPath(), "skilltrees");
        getDataFolder().mkdirs();
        boolean createDefaultSkilltree = skilltreeFolder.mkdirs();
        boolean createLocaleReadme = new File(getDataFolder(), "locale").mkdirs();
        new File(getDataFolder(), "logs").mkdirs();

        if (!createDefaultSkilltree) {
            File legacyDefaultSkilltree = new File(skilltreeFolder, "default.st");
            if (legacyDefaultSkilltree.exists()) {
                if (Util.getSha256FromFile(legacyDefaultSkilltree) == -4323392001800132707L) {
                    createDefaultSkilltree = true;
                    legacyDefaultSkilltree.delete();
                }
            }
        }

        if (createDefaultSkilltree) {
            File skilltreeFile = new File(skilltreeFolder, "Combat.st.json");
            if (!skilltreeFile.exists()) {
                platformHelper.copyResource(this, "skilltrees/Combat.st.json", new File(skilltreeFolder, "Combat.st.json"));
            }
            skilltreeFile = new File(skilltreeFolder, "Farm.st.json");
            if (!skilltreeFile.exists()) {
                platformHelper.copyResource(this, "skilltrees/Farm.st.json", new File(skilltreeFolder, "Farm.st.json"));
            }
            skilltreeFile = new File(skilltreeFolder, "PvP.st.json");
            if (!skilltreeFile.exists()) {
                platformHelper.copyResource(this, "skilltrees/PvP.st.json", new File(skilltreeFolder, "PvP.st.json"));
            }
            skilltreeFile = new File(skilltreeFolder, "Ride.st.json");
            if (!skilltreeFile.exists()) {
                platformHelper.copyResource(this, "skilltrees/Ride.st.json", new File(skilltreeFolder, "Ride.st.json"));
            }
            skilltreeFile = new File(skilltreeFolder, "Utility.st.json");
            if (!skilltreeFile.exists()) {
                platformHelper.copyResource(this, "skilltrees/Utility.st.json", new File(skilltreeFolder, "Utility.st.json"));
            }
            MyPetApi.getLogger().info("Default skilltree files created.");
        }

        // 加载技能树
        MyPetApi.getSkilltreeManager().clearSkilltrees();
        SkillTreeLoaderJSON.loadSkilltrees(new File(getDataFolder(), "skilltrees"));

        for (int i = 0; i <= Configuration.Misc.MAX_STORED_PET_COUNT; i++) {
            try {
                Bukkit.getPluginManager().addPermission(new Permission("MyPet.petstorage.limit." + i));
            } catch (Exception ignored) {
            }
        }

        if (createLocaleReadme) {
            platformHelper.copyResource(this, "locale-readme.txt", new File(getDataFolder(), "locale" + File.separator + "readme.txt"));
        }
        Translation.init();

        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith("MyPet-")) {
                team.unregister();
            }
        }

        // 初始化存储库
        if (Configuration.Repository.REPOSITORY_TYPE.equalsIgnoreCase("NBT")) {
            Configuration.Repository.REPOSITORY_TYPE = "SQLite";
            Configuration.Repository.CONVERT_FROM = "NBT";
            repo = new SQLiteRepository();
            try {
                repo.init();
            } catch (RepositoryInitException e) {
                e.printStackTrace();
                repo = null;
            }
        } else if (Configuration.Repository.REPOSITORY_TYPE.equalsIgnoreCase("MySQL")) {
            repo = new MySQLRepository();
            try {
                repo.init();
                MyPetApi.getLogger().info("MySQL connection successful.");
            } catch (RepositoryInitException e) {
                MyPetApi.getLogger().warning("MySQL connection failed!");
                e.printStackTrace();
                repo = null;
            }
        } else if (Configuration.Repository.REPOSITORY_TYPE.equalsIgnoreCase("MongoDB")) {
            repo = new MongoDBRepository();
            try {
                repo.init();
                MyPetApi.getLogger().info("MongoDB connection successful.");
            } catch (RepositoryInitException e) {
                MyPetApi.getLogger().warning("MongoDB connection failed!");
                e.printStackTrace();
                repo = null;
            }
        }

        if (repo == null) {
            repo = new SQLiteRepository();
            try {
                repo.init();
                MyPetApi.getLogger().info("SQLite connection successful.");
            } catch (RepositoryInitException ignored) {
                MyPetApi.getLogger().warning("SQLite connection failed!");
                setEnabled(false);
                return;
            }
        }

        Converter.convert();

        if (repo instanceof Scheduler) {
            Timer.addTask((Scheduler) repo);
        }

        File shopConfig = new File(getDataFolder(), "pet-shops.yml");
        if (!shopConfig.exists()) {
            platformHelper.copyResource(this, "pet-shops.yml", shopConfig);
        }
        new ShopManager();

        Timer.startTimer();

        updater.waitForDownload();

        pluginHookManager.enableHooks();
        serviceManager.activate(Load.State.AfterHooks);

        this.isReady = true;

        ContributorCheck.startRefreshTask();

        serviceManager.activate(Load.State.OnReady);

        // 为在线玩家加载宠物
        new BukkitRunnable() {
            @Override
            public void run() {
                for (final Player player : getServer().getOnlinePlayers()) {
                    repo.getMyPetPlayer(player, new RepositoryCallback<MyPetPlayer>() {
                        @Override
                        public void callback(final MyPetPlayer p) {
                            if (p != null) {
                                final MyPetPlayerImpl onlinePlayer = (MyPetPlayerImpl) p;

                                onlinePlayer.setLastKnownName(player.getName());
                                if (!player.getUniqueId().equals(onlinePlayer.getOfflineUUID())) {
                                    if (onlinePlayer.getMojangUUID() == null) {
                                        onlinePlayer.setMojangUUID(player.getUniqueId());
                                    }
                                    onlinePlayer.setOnlineMode(true);
                                }

                                playerManager.setOnline(onlinePlayer);

                                final WorldGroup joinGroup = WorldGroup.getGroupByWorld(player.getWorld().getName());
                                if (joinGroup.isDisabled()) {
                                    return;
                                }
                                if (onlinePlayer.hasMyPet()) {
                                    MyPet myPet = onlinePlayer.getMyPet();
                                    if (!myPet.getWorldGroup().equals(joinGroup.getName())) {
                                        myPetManager.deactivateMyPet(onlinePlayer, true);
                                    }
                                }

                                if (!onlinePlayer.hasMyPet() && onlinePlayer.hasMyPetInWorldGroup(joinGroup.getName())) {
                                    final UUID petUUID = onlinePlayer.getMyPetForWorldGroup(joinGroup.getName());

                                    MyPetApi.getRepository().getMyPet(petUUID, new RepositoryCallback<StoredMyPet>() {
                                        @Override
                                        public void callback(StoredMyPet storedMyPet) {
                                            myPetManager.activateMyPet(storedMyPet);

                                            if (onlinePlayer.hasMyPet()) {
                                                final MyPet myPet = onlinePlayer.getMyPet();
                                                final MyPetPlayer myPetPlayer = myPet.getOwner();
                                                if (myPet.wantsToRespawn()) {
                                                    if (myPetPlayer.hasMyPet()) {
                                                        MyPet runMyPet = myPetPlayer.getMyPet();
                                                        switch (runMyPet.createEntity()) {
                                                            case Canceled:
                                                                runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Prevent", myPet.getOwner()), runMyPet.getPetName()));
                                                                break;
                                                            case NoSpace:
                                                                runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.NoSpace", myPet.getOwner()), runMyPet.getPetName()));
                                                                break;
                                                            case NotAllowed:
                                                                runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.No.AllowedHere", myPet.getOwner()), myPet.getPetName()));
                                                                break;
                                                            case Dead:
                                                                if (Configuration.Respawn.DISABLE_AUTO_RESPAWN) {
                                                                    runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Call.Dead", myPet.getOwner()), myPet.getPetName()));
                                                                } else {
                                                                    runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Respawn.In", myPet.getOwner()), myPet.getPetName(), myPet.getRespawnTime()));
                                                                }
                                                                break;
                                                            case Flying:
                                                                runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Flying", myPet.getOwner()), myPet.getPetName()));
                                                                break;
                                                            case Success:
                                                                runMyPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Command.Call.Success", myPet.getOwner()), runMyPet.getPetName()));
                                                                break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    });
                                }
                                onlinePlayer.checkForContribution();
                            }
                        }
                    });
                }
            }
        }.runTaskLater(this, 0);
    }

    private void registerServices() {
        serviceManager.registerService(RepositoryMyPetConverterService.class);
        serviceManager.registerService(ItemDatabase.class);
        serviceManager.registerService(LeashFlagManager.class);
        serviceManager.registerService(ExperienceCache.class);
        serviceManager.registerService(ExperienceCalculatorManager.class);
        serviceManager.registerService(SkillManager.class);
        serviceManager.registerService(SkilltreeManager.class);
        serviceManager.registerService(ShopManager.class);
        serviceManager.registerService(de.Keyle.MyPet.services.DefaultCreakingService.class);
    }

    private void registerHooks() {
        pluginHookManager.registerHook(AncientHook.class);
        pluginHookManager.registerHook(BattleArenaHook.class);
        pluginHookManager.registerHook(BossShopProHook.class);
        pluginHookManager.registerHook(CitizensHook.class);
        pluginHookManager.registerHook(CombatLogXHook.class);
        pluginHookManager.registerHook(FabledSkyBlockHook.class);
        pluginHookManager.registerHook(FactionsHook.class);
        pluginHookManager.registerHook(GangsPlusHook.class);
        pluginHookManager.registerHook(GriefPreventionHook.class);
        pluginHookManager.registerHook(GriefPreventionPlusHook.class);
        pluginHookManager.registerHook(GuildsHook.class);
        pluginHookManager.registerHook(HeroesHook.class);
        pluginHookManager.registerHook(KingdomsHook.class);
        pluginHookManager.registerHook(LandsHook.class);
        pluginHookManager.registerHook(MagicSpellsHook.class);
        pluginHookManager.registerHook(McMMOHook.class);
        pluginHookManager.registerHook(MiniaturePetsHook.class);
        pluginHookManager.registerHook(MinigamesHook.class);
        pluginHookManager.registerHook(MobArenaHook.class);
        pluginHookManager.registerHook(MobStackerAHook.class);
        pluginHookManager.registerHook(MobStackerBHook.class);
        pluginHookManager.registerHook(MythicMobsHook.class);
        pluginHookManager.registerHook(NoCheatPlusHook.class);
        pluginHookManager.registerHook(PlaceholderApiHook.class);
        pluginHookManager.registerHook(PlotSquaredHook.class);
        pluginHookManager.registerHook(PreciousStonesHook.class);
        pluginHookManager.registerHook(PremiumVanishHook.class);
        pluginHookManager.registerHook(ProtocolLibHook.class);
        pluginHookManager.registerHook(PvPArenaHook.class);
        pluginHookManager.registerHook(PvPDiffTimerHook.class);
        pluginHookManager.registerHook(PvPManagerHook.class);
        pluginHookManager.registerHook(RedProtectHook.class);
        pluginHookManager.registerHook(ResidenceHook.class);
        pluginHookManager.registerHook(SimpleClansHook.class);
        pluginHookManager.registerHook(SkillApiHook.class);
        pluginHookManager.registerHook(StackMobHook.class);
        pluginHookManager.registerHook(SuperVanishHook.class);
        pluginHookManager.registerHook(SurvivalGamesHook.class);
        pluginHookManager.registerHook(TownyHook.class);
        pluginHookManager.registerHook(UltimateSurvivalGamesHook.class);
        pluginHookManager.registerHook(VaultHook.class);
        pluginHookManager.registerHook(WorldGuardHook.class);
        pluginHookManager.registerHook(WorldGuardCustomFlagsHook.class);
    }

    public static void registerSkills() {
        MyPetApi.getSkillManager().registerSkill(BackpackImpl.class);
        MyPetApi.getSkillManager().registerSkill(HealImpl.class);
        MyPetApi.getSkillManager().registerSkill(PickupImpl.class);
        MyPetApi.getSkillManager().registerSkill(BehaviorImpl.class);
        MyPetApi.getSkillManager().registerSkill(DamageImpl.class);
        MyPetApi.getSkillManager().registerSkill(ControlImpl.class);
        MyPetApi.getSkillManager().registerSkill(LifeImpl.class);
        MyPetApi.getSkillManager().registerSkill(PoisonImpl.class);
        MyPetApi.getSkillManager().registerSkill(RideImpl.class);
        MyPetApi.getSkillManager().registerSkill(ThornsImpl.class);
        MyPetApi.getSkillManager().registerSkill(FireImpl.class);
        MyPetApi.getSkillManager().registerSkill(BeaconImpl.class);
        MyPetApi.getSkillManager().registerSkill(WitherImpl.class);
        MyPetApi.getSkillManager().registerSkill(LightningImpl.class);
        MyPetApi.getSkillManager().registerSkill(SlowImpl.class);
        MyPetApi.getSkillManager().registerSkill(KnockbackImpl.class);
        MyPetApi.getSkillManager().registerSkill(RangedImpl.class);
        MyPetApi.getSkillManager().registerSkill(SprintImpl.class);
        MyPetApi.getSkillManager().registerSkill(StompImpl.class);
        MyPetApi.getSkillManager().registerSkill(ShieldImpl.class);
        MyPetApi.getSkillManager().registerSkill(BleedImpl.class);
    }

    public static void registerLeashFlags() {
        MyPetApi.getLeashFlagManager().registerLeashFlag(new AdultFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new AngryFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new BabyFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new BelowHpFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new CanBreedFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new ChanceFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new ImpossibleFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new LowHpFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new ScreamingFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new SizeFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new TamedFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new UserCreatedFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new WildFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new WorldFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new PermissionFlag());
        MyPetApi.getLeashFlagManager().registerLeashFlag(new HeartLinkedFlag());
    }

    public static void registerSkilltreeRequirements() {
        MyPetApi.getSkilltreeManager().registerRequirement(new NoSkilltreeRequirement());
        MyPetApi.getSkilltreeManager().registerRequirement(new PermissionRequirement());
        MyPetApi.getSkilltreeManager().registerRequirement(new PetLevelRequirement());
        MyPetApi.getSkilltreeManager().registerRequirement(new SkilltreeRequirement());
    }

    @Override
    public PluginHookManager getPluginHookManager() {
        return pluginHookManager;
    }

    @Override
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    public File getFile() {
        return super.getFile();
    }

    @Override
    public MyPetInfo getMyPetInfo() {
        return petInfo;
    }

    @Override
    public EntityRegistry getEntityRegistry() {
        return entityRegistry;
    }

    @Override
    public CompatUtil getCompatUtil() {
        return compatUtil;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public MyPetManager getMyPetManager() {
        return myPetManager;
    }

    public HookHelper getHookHelper() {
        return hookHelper;
    }

    public Repository getRepository() {
        return repo;
    }

    public PlatformHelper getPlatformHelper() {
        return platformHelper;
    }

    @Override
    public boolean isDisabling() {
        return isDisabling;
    }

    private void printSplashScreen(String updateStatus) {
        String G = ChatColor.DARK_GREEN.toString();
        String R = ChatColor.RESET.toString();
        String version = MyPetVersion.getFormattedVersion();
        String compatInfo = compatUtil.getInternalVersion() != null
                ? G + "✔" + R + " Compatible with " + compatUtil.getMinecraftVersion() + " (" + compatUtil.getInternalVersion() + ")"
                : null;

        String dbType = Configuration.Repository.REPOSITORY_TYPE;

        String splash = String.join("\n",
                "",
                G + "          ▄▄       ",
                G + "    ▄██▄ ████      " + G + "  MyPet " + R + version,
                G + "    ████ ▀██▀      " + G + "  Created by Keyle | Maintained by UserDerezzed",
                G + "  ▄▄ ▀▀      ▄██▄  " + G + "  2011-" + Year.now(),
                G + " ████  ▄███▄ ▀██▀  ",
                G + "  ▀▀ ▄███████▄     " + (updateStatus != null ? R + "  " + updateStatus : ""),
                G + "   ▄███████████▄   " + (compatInfo != null ? R + "  " + compatInfo : ""),
                G + "   ▀███▀▀▀▀▀███▀   " + R + "  " + "Connecting to " + dbType + "...",
                "",
                G + "Please consider supporting active development: https://ko-fi.com/userderezzed"
        );

        getLogger().info(splash);
    }
}