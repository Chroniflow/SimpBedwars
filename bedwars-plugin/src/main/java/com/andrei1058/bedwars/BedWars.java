/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.levels.Level;
import com.andrei1058.bedwars.api.party.Party;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.ArenaManager;
import com.andrei1058.bedwars.arena.ProxyLobbyConnector;
import com.andrei1058.bedwars.arena.VoidChunkGenerator;
import com.andrei1058.bedwars.arena.despawnables.TargetListener;
import com.andrei1058.bedwars.arena.feature.SpoilPlayerTNTFeature;
import com.andrei1058.bedwars.arena.spectator.SpectatorListeners;
import com.andrei1058.bedwars.arena.team.PreGameSquadManager;
import com.andrei1058.bedwars.arena.team.PreGameSquadGUI;
import com.andrei1058.bedwars.arena.team.PreGameTeamSelectionGUI;
import com.andrei1058.bedwars.arena.team.PreGameTeamSelectionManager;
import com.andrei1058.bedwars.arena.stats.DefaultStatsHandler;
import com.andrei1058.bedwars.arena.tasks.OneTick;
import com.andrei1058.bedwars.arena.tasks.Refresh;
import com.andrei1058.bedwars.arena.upgrades.BaseListener;
import com.andrei1058.bedwars.arena.upgrades.HealPoolListner;
import com.andrei1058.bedwars.commands.bedwars.MainCommand;
import com.andrei1058.bedwars.commands.leave.LeaveCommand;
import com.andrei1058.bedwars.commands.party.PartyCommand;
import com.andrei1058.bedwars.commands.rejoin.RejoinCommand;
import com.andrei1058.bedwars.commands.shout.ShoutCommand;
import com.andrei1058.bedwars.configuration.*;
import com.andrei1058.bedwars.database.Database;
import com.andrei1058.bedwars.database.SQLite;
import com.andrei1058.bedwars.halloween.HalloweenSpecial;
import com.andrei1058.bedwars.language.SimplifiedChinese;
import com.andrei1058.bedwars.levels.internal.InternalLevel;
import com.andrei1058.bedwars.levels.internal.LevelListeners;
import com.andrei1058.bedwars.listeners.*;
import com.andrei1058.bedwars.listeners.arenaselector.ArenaSelectorListener;
import com.andrei1058.bedwars.listeners.blockstatus.BlockStatusListener;
import com.andrei1058.bedwars.listeners.chat.ChatAFK;
import com.andrei1058.bedwars.listeners.chat.ChatFormatting;
import com.andrei1058.bedwars.listeners.joinhandler.*;
import com.andrei1058.bedwars.lobbysocket.ArenaSocket;
import com.andrei1058.bedwars.lobbysocket.LoadedUsersCleaner;
import com.andrei1058.bedwars.lobbysocket.SendTask;
import com.andrei1058.bedwars.maprestore.internal.InternalAdapter;
import com.andrei1058.bedwars.metrics.MetricsManager;
import com.andrei1058.bedwars.money.internal.MoneyListeners;
import com.andrei1058.bedwars.shop.ShopManager;
import com.andrei1058.bedwars.sidebar.*;
import com.andrei1058.bedwars.stats.StatsManager;
import com.andrei1058.bedwars.support.citizens.CitizensListener;
import com.andrei1058.bedwars.support.citizens.JoinNPC;
import com.andrei1058.bedwars.support.papi.PAPISupport;
import com.andrei1058.bedwars.support.papi.SupportPAPI;
import com.andrei1058.bedwars.support.party.NoParty;
import com.andrei1058.bedwars.support.party.PAF;
import com.andrei1058.bedwars.support.party.PAFBungeecordRedisApi;
import com.andrei1058.bedwars.support.party.PartiesAdapter;
import com.andrei1058.bedwars.support.vault.*;
import com.andrei1058.bedwars.support.vipfeatures.VipFeatures;
import com.andrei1058.bedwars.support.vipfeatures.VipListeners;
import com.andrei1058.bedwars.support.version.v1_21_R3.v1_21_R3;
import com.andrei1058.vipfeatures.api.IVipFeatures;
import com.andrei1058.vipfeatures.api.MiniGameAlreadyRegistered;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

@SuppressWarnings({"WeakerAccess", "CallToPrintStackTrace"})
public class BedWars extends JavaPlugin {

    private static ServerType serverType = ServerType.MULTIARENA;
    public static boolean debug = false, autoscale = false;
    public static String mainCmd = "bw", link = "https://www.spigotmc.org/resources/50942/";
    public static ConfigManager signs, generators;
    public static MainConfig config;
    public static ShopManager shop;
    public static StatsManager statsManager;
    public static BedWars plugin;
    public static VersionSupport nms;

    public static boolean isPaper = false;

    private static Party party = new NoParty();
    private static Chat chat = new NoChat();
    protected static Level level;
    private static Economy economy = new NoEconomy();
    private static final String version = Bukkit.getBukkitVersion().split("-")[0];
    private static String lobbyWorld = "";
    private static boolean shuttingDown = false;

    public static ArenaManager arenaManager = new ArenaManager();

    //remote database
    private static Database remoteDatabase;

    private boolean serverSoftwareSupport = true;
    private boolean vaultSupportInitialized;
    private boolean vaultRetryListenerRegistered;
    private ProxyLobbyConnector proxyLobbyConnector;

    private static com.andrei1058.bedwars.api.BedWars api;

    @Override
    public void onLoad() {

        isPaper = detectPaper();
        if (!isPaper) {
            this.getLogger().severe("SimpMC-BedWars supports Paper 1.21.11 only.");
            this.getLogger().severe("Please run this plugin on Paper or a compatible Paper fork.");
            serverSoftwareSupport = false;
            return;
        }

        if (detectFolia()) {
            this.getLogger().severe("Folia is not supported by this SimpMC-BedWars build.");
            serverSoftwareSupport = false;
            return;
        }

        if (!isExactMinecraftVersion(1, 21, 11)) {
            serverSoftwareSupport = false;
            this.getLogger().severe("I can't run on your Minecraft version: " + version);
            this.getLogger().severe("This build supports Paper 1.21.11 only because newer world formats are not yet supported.");
            return;
        }

        plugin = this;

        api = new API();
        Bukkit.getServicesManager().register(com.andrei1058.bedwars.api.BedWars.class, api, this, ServicePriority.Highest);

        nms = new v1_21_R3(this, version);

        this.getLogger().info("Loading Paper API support for Minecraft " + version + ".");

        // Setup the single bundled player language. Legacy language files are
        // left on disk but are no longer loaded or exposed to players.
        new SimplifiedChinese();

        // Persist the shared Chinese documentation after its defaults load.
        for (Language language : Language.getLanguages()) {
            language.addChineseDocumentation();
            language.save();
        }

        config = new MainConfig(this, "config");

        generators = new GeneratorsConfig(this, "generators", this.getDataFolder().getPath());
        // Initialize signs config after the main config
        if (getServerType() != ServerType.BUNGEE) {
            signs = new SignsConfig(this, "signs", this.getDataFolder().getPath());
        }
    }

    @Override
    public void onEnable() {
        if (!serverSoftwareSupport) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        nms.registerVersionListeners();

        api.setRestoreAdapter(new InternalAdapter(this));
        getLogger().info("Using internal world restore system.");

        /* Register commands */
        nms.registerCommand(mainCmd, new MainCommand(mainCmd));

        this.registerDelayedCommands();

        /* Setup the BungeeCord-compatible proxy messaging channel. */
        proxyLobbyConnector = new ProxyLobbyConnector(this);
        proxyLobbyConnector.register();

        // define logger
        var out = plugin.getLogger();

        /* Check if lobby location is set. Required for non Bungee servers */
        if (config.getLobbyWorldName().isEmpty() && serverType != ServerType.BUNGEE) {
            out.info("Lobby location is not set yet. Use /bw setLobby after setup if this server needs a lobby.");
        }

        /* Load lobby world if not main level
         * when the server finishes loading. */
        if (getServerType() == ServerType.MULTIARENA)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!config.getLobbyWorldName().isEmpty()) {
                    if (Bukkit.getWorld(config.getLobbyWorldName()) == null && new File(Bukkit.getWorldContainer(), config.getLobbyWorldName() + "/level.dat").exists()) {
                        if (!config.getLobbyWorldName().equalsIgnoreCase(Bukkit.getServer().getWorlds().get(0).getName())) {
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                Bukkit.createWorld(new WorldCreator(config.getLobbyWorldName()));

                                if (Bukkit.getWorld(config.getLobbyWorldName()) != null) {
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> Objects.requireNonNull(Bukkit.getWorld(config.getLobbyWorldName()))
                                            .getEntities().stream().filter(e -> e instanceof Monster).forEach(Entity::remove), 20L);
                                }
                            }, 100L);
                        }
                    }
                    Location l = config.getConfigLoc("lobbyLoc");
                    if (l != null) {
                        World w = Bukkit.getWorld(config.getLobbyWorldName());
                        if (w != null) {
                            w.setSpawnLocation(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }
                }
            }, 1L);

        // Register events
        registerEvents(
                new EnderPearlLanded(), new QuitAndTeleportListener(), new ArenaWorldProtection(), new BreakPlace(),
                new PlacedBlockListener(), new DamageDeathMove(),
                new Inventory(), new Interact(), new LobbyProtection(), new RefreshGUI(), new HungerWeatherSpawn(), new CmdProcess(),
                new FireballListener(), new EggBridge(), new SpectatorListeners(), new BaseListener(),
                new TargetListener(), new Warnings(this), new ChatAFK(),
                new GameEndListener(), new DefaultStatsHandler(), new VanillaAdvancementListener(), new MoneyListeners(),
                PreGameSquadManager.getInstance(), PreGameSquadGUI.getInstance(),
                PreGameTeamSelectionManager.getInstance(), PreGameTeamSelectionGUI.getInstance()
        );

        if (config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_HEAL_POOL_ENABLE)) {
            registerEvents(new HealPoolListner());
        }

        if (getServerType() == ServerType.BUNGEE) {
            if (autoscale) {
                //registerEvents(new ArenaListeners());
                ArenaSocket.lobbies.addAll(config.getList(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_LOBBY_SERVERS));
                new SendTask();
                registerEvents(new AutoscaleListener(), new JoinListenerBungee());
                Bukkit.getScheduler().runTaskTimer(this, new LoadedUsersCleaner(), 60L, 60L);
            } else {
                registerEvents(new ServerPingListener(), new JoinListenerBungeeLegacy());
            }
        } else if (getServerType() == ServerType.MULTIARENA || getServerType() == ServerType.SHARED) {
            registerEvents(new ArenaSelectorListener(), new BlockStatusListener());
            if (getServerType() == ServerType.MULTIARENA) {
                registerEvents(new JoinListenerMultiArena());
            } else {
                registerEvents(new JoinListenerShared());
            }
        }

        WorldLoadListener worldLoadListener = new WorldLoadListener();
        registerEvents(worldLoadListener);
        worldLoadListener.enforceLoadedWorlds();

        if (!(getServerType() == ServerType.BUNGEE && autoscale)) {
            registerEvents(new JoinHandlerCommon());
        }

        // Register setup-holograms fix
        registerEvents(new ChunkLoad());

        registerEvents(new InvisibilityPotionListener());

        /* Load join signs. */
        loadArenasAndSigns();

        statsManager = new StatsManager();

        /* Party support */
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (config.getYml().getBoolean(ConfigPath.GENERAL_CONFIGURATION_ALLOW_PARTIES)) {

                if (getServer().getPluginManager().isPluginEnabled("Parties")) {
                    out.info("Hook into Parties (by AlessioDP) support!");
                    party = new PartiesAdapter();
                } else if (Bukkit.getServer().getPluginManager().isPluginEnabled("PartyAndFriends")) {
                    out.info("Hook into Party and Friends for Spigot (by Simonsator) support!");
                    party = new PAF();
                } else if (Bukkit.getServer().getPluginManager().isPluginEnabled("Spigot-Party-API-PAF")) {
                    out.info("Hook into Spigot Party API for Party and Friends Extended (by Simonsator) support!");
                    party = new PAFBungeecordRedisApi();
                }

                if (party instanceof NoParty) {
                    party = new com.andrei1058.bedwars.support.party.Internal();
                    out.info("Loading internal Party system. /party");
                }
            } else {
                party = new NoParty();
            }
        }, 10L);

        /* Levels support */
        setLevelAdapter(new InternalLevel());

        /* Register tasks */
        Bukkit.getScheduler().runTaskTimer(this, new Refresh(), 20L, 20L);
        //new Refresh().runTaskTimer(this, 20L, 20L);

        if (config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_PERFORMANCE_ROTATE_GEN)) {
            //new OneTick().runTaskTimer(this, 120, 1);
            Bukkit.getScheduler().runTaskTimer(this, new OneTick(), 120, 1);
        }

        /* Register NMS entities */
        nms.registerEntities();

        /* Database support */
        if (config.getBoolean("database.enable")) {
            com.andrei1058.bedwars.database.MySQL mySQL = new com.andrei1058.bedwars.database.MySQL();
            long time = System.currentTimeMillis();
            if (!mySQL.connect()) {
                this.getLogger().severe("Could not connect to database! Please verify your credentials and make sure that the server IP is whitelisted in MySQL.");
                remoteDatabase = new SQLite();
            } else {
                remoteDatabase = mySQL;
            }
            if (System.currentTimeMillis() - time >= 5000) {
                this.getLogger().severe("It took " + ((System.currentTimeMillis() - time) / 1000) + " ms to establish a database connection!\n" +
                        "Using this remote connection is not recommended!");
            }
            remoteDatabase.init();
        } else {
            remoteDatabase = new SQLite();
            remoteDatabase.init();
        }

        /* Citizens support */
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (this.getServer().getPluginManager().getPlugin("Citizens") != null) {
                JoinNPC.setCitizensSupport(true);
                out.info("Hook into Citizens support. /bw npc");
                registerEvents(new CitizensListener());
            }

            //spawn NPCs
            try {
                JoinNPC.spawnNPCs();
            } catch (Exception e) {
                this.getLogger().severe("Could not spawn CmdJoin NPCs. Make sure you have right version of Citizens for your server!");
                JoinNPC.setCitizensSupport(false);
            }
        }, 5L);

        /* Save messages for stats gui items if custom items added, for each language */
        Language.setupCustomStatsMessages();

        /* PlaceholderAPI Support */
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            out.info("Hooked into PlaceholderAPI support!");
            new PAPISupport().register();
            SupportPAPI.setSupportPAPI(new SupportPAPI.withPAPI());
        }
        initializeVaultSupport();

        /* Chat support */
        if (config.getBoolean(ConfigPath.GENERAL_CHAT_FORMATTING)) {
            registerEvents(new ChatFormatting());
        }

        /* Protect glass walls from tnt explosion */
        nms.registerTntWhitelist(
                (float) config.getDouble(ConfigPath.GENERAL_TNT_PROTECTION_END_STONE_BLAST),
                (float) config.getDouble(ConfigPath.GENERAL_TNT_PROTECTION_GLASS_BLAST)
        );

        /* Prevent issues on reload */
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kickPlayer("SimpMC-BedWars was reloaded. Please restart the server instead of reloading plugins.");
        }

        /* Load sounds configuration */
        Sounds.init();

        /* Initialize shop */
        shop = new ShopManager();

        //Leave this code at the end of the enable method
        for (Language l : Language.getLanguages()) {
            l.setupUnSetCategories();
            Language.addDefaultMessagesCommandItems(l);
        }

        LevelsConfig.init();

        /* Load Money Configuration */
        MoneyConfig.init();

        // bStats metrics
        MetricsManager.initService(this);

        if (Bukkit.getPluginManager().getPlugin("VipFeatures") != null) {
            try {
                IVipFeatures vf = Bukkit.getServicesManager().getRegistration(IVipFeatures.class).getProvider();
                vf.registerMiniGame(new VipFeatures(this));
                registerEvents(new VipListeners(vf));
                out.log(java.util.logging.Level.INFO, "Hook into VipFeatures support.");
            } catch (Exception e) {
                out.warning("Could not load support for VipFeatures.");
            } catch (MiniGameAlreadyRegistered miniGameAlreadyRegistered) {
                miniGameAlreadyRegistered.printStackTrace();
            }
        }

        Bukkit.getScheduler().runTaskLater(this,
                () -> out.info("This server is running in " + getServerType() + " with auto-scale " + autoscale),
                100L
        );

        // Initialize team upgrades
        com.andrei1058.bedwars.upgrades.UpgradesManager.init();

        // Initialize sidebar manager
        if (SidebarService.init(this)) {
            out.info("Initializing Paper scoreboard sidebar support.");
        } else {
            this.getLogger().warning("Paper scoreboard sidebar support could not be initialized; continuing without built-in sidebars.");
        }

        // Halloween Special
        HalloweenSpecial.init();

        // TNT Spoil Feature
        SpoilPlayerTNTFeature.init();

    }

    private void registerDelayedCommands() {
        ShoutCommand shout = new ShoutCommand("shout");
        PluginCommand declaredShout = getCommand("shout");
        if (declaredShout != null) {
            declaredShout.setExecutor((sender, command, label, args) ->
                    shout.execute(sender, label, args));
        } else if (!nms.isBukkitCommandRegistered("shout")) {
            nms.registerCommand("shout", shout);
        }
        nms.registerCommand("rejoin", new RejoinCommand("rejoin"));
        if (!(nms.isBukkitCommandRegistered("leave") && getServerType() == ServerType.BUNGEE)) {
            nms.registerCommand("leave", new LeaveCommand("leave"));
        }
        if (getServerType() != ServerType.BUNGEE && config.getBoolean(ConfigPath.GENERAL_ENABLE_PARTY_CMD)) {
            Bukkit.getLogger().info("Registering /party command..");
            nms.registerCommand("party", new PartyCommand("party"));
        }
    }

    public void onDisable() {
        shuttingDown = true;
        if (!serverSoftwareSupport) return;
        if (getServerType() == ServerType.BUNGEE) {
            ArenaSocket.disable();
        }
        for (IArena a : new LinkedList<>(Arena.getArenas())) {
            try {
                a.disable();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (remoteDatabase != null) {
            remoteDatabase.close();
        }

        if (proxyLobbyConnector != null) {
            proxyLobbyConnector.close();
            proxyLobbyConnector = null;
        }

    }

    public ProxyLobbyConnector getProxyLobbyConnector() {
        return proxyLobbyConnector;
    }

    private void loadArenasAndSigns() {

        api.getRestoreAdapter().convertWorlds();

        File dir = new File(plugin.getDataFolder(), "/Arenas");
        if (dir.exists()) {
            List<File> files = new ArrayList<>();
            File[] fls = dir.listFiles();
            for (File fl : Objects.requireNonNull(fls)) {
                if (fl.isFile()) {
                    if (fl.getName().endsWith(".yml")) {
                        files.add(fl);
                    }
                }
            }

            if (serverType == ServerType.BUNGEE && !autoscale) {
                if (files.isEmpty()) {
                    this.getLogger().log(java.util.logging.Level.WARNING, "Could not find any arena!");
                    return;
                }
                Random r = new Random();
                int x = r.nextInt(files.size());
                String name = files.get(x).getName().replace(".yml", "");
                new Arena(name, null);
            } else {
                for (File file : files) {
                    new Arena(file.getName().replace(".yml", ""), null);
                }
            }
        }
    }

    public static void registerEvents(Listener... listeners) {
        Arrays.stream(listeners).forEach(l -> plugin.getServer().getPluginManager().registerEvents(l, plugin));
    }

    /**
     * 初始化 Vault 服务。服务提供者可能晚于本插件注册，因此同时监听后续变化。
     */
    private void initializeVaultSupport() {
        if (vaultSupportInitialized) return;
        try {
            Class.forName("net.milkbowl.vault.economy.Economy", false, getClass().getClassLoader());
            VaultIntegration integration = new VaultIntegration(this);
            registerEvents(integration);
            vaultSupportInitialized = true;
            Bukkit.getScheduler().runTask(this, integration::refresh);
        } catch (ClassNotFoundException | LinkageError exception) {
            chat = new NoChat();
            economy = new NoEconomy();
            getLogger().info("未检测到 Vault API；经济奖励和 Vault 聊天前后缀已停用。");
            if (!vaultRetryListenerRegistered) {
                vaultRetryListenerRegistered = true;
                registerEvents(new VaultServiceBootstrapListener());
            }
        }
    }

    /**
     * 兼容未在 plugin.yml 中声明自身为 Vault、且晚于本插件加载的实现。
     */
    private final class VaultServiceBootstrapListener implements Listener {

        @EventHandler
        public void onServiceRegister(ServiceRegisterEvent event) {
            String serviceName = event.getProvider().getService().getName();
            if (serviceName.startsWith("net.milkbowl.vault.")) {
                initializeVaultSupport();
            }
        }
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static void setServerType(ServerType serverType) {
        BedWars.serverType = serverType;
        if (serverType == ServerType.BUNGEE) autoscale = true;
    }

    public static void setAutoscale(boolean autoscale) {
        BedWars.autoscale = autoscale;
    }

    public static void debug(String message) {
        if (debug) {
            plugin.getLogger().info("DEBUG: " + message);
        }
    }

    private static boolean detectPaper() {
        for (String className : Arrays.asList(
                "com.destroystokyo.paper.PaperConfig",
                "io.papermc.paper.configuration.Configuration",
                "io.papermc.paper.plugin.configuration.PluginMeta"
        )) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Bukkit.getName().toLowerCase(Locale.ROOT).contains("paper")
                || Bukkit.getVersion().toLowerCase(Locale.ROOT).contains("paper");
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return Bukkit.getName().toLowerCase(Locale.ROOT).contains("folia");
        }
    }

    private static boolean isExactMinecraftVersion(int major, int minor, int patch) {
        String minecraftVersion = Bukkit.getBukkitVersion().split("-")[0];
        String[] parts = minecraftVersion.split("\\.");
        return parseVersionPart(parts, 0) == major
                && parseVersionPart(parts, 1) == minor
                && parseVersionPart(parts, 2) == patch;
    }

    private static int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static ServerType getServerType() {
        return serverType;
    }

    public static Party getParty() {
        return party;
    }

    public static Chat getChatSupport() {
        return chat;
    }

    /**
     * 设置聊天前后缀适配器。
     *
     * @param chatAdapter 新适配器
     */
    public static void setChatAdapter(Chat chatAdapter) {
        chat = Objects.requireNonNull(chatAdapter, "chatAdapter");
    }

    /**
     * Get current levels manager.
     */
    public static Level getLevelSupport() {
        return level;
    }

    /**
     * Set the levels manager.
     * You can use this to add your own levels manager just implement
     * the Level interface so the plugin will be able to display
     * the level internally.
     */

    public static void setLevelAdapter(Level levelsManager) {
        if (levelsManager instanceof InternalLevel) {
            if (LevelListeners.instance == null) {
                Bukkit.getPluginManager().registerEvents(new LevelListeners(), BedWars.plugin);
            }
        } else {
            if (LevelListeners.instance != null) {
                PlayerJoinEvent.getHandlerList().unregister(LevelListeners.instance);
                PlayerQuitEvent.getHandlerList().unregister(LevelListeners.instance);
                LevelListeners.instance = null;
            }
        }
        level = levelsManager;
    }

    public static Economy getEconomy() {
        return economy;
    }

    /**
     * 设置经济适配器。
     *
     * @param economyAdapter 新适配器
     */
    public static void setEconomyAdapter(Economy economyAdapter) {
        economy = Objects.requireNonNull(economyAdapter, "economyAdapter");
    }

    public static ConfigManager getGeneratorsCfg() {
        return generators;
    }

    public static void setLobbyWorld(String lobbyWorld) {
        BedWars.lobbyWorld = lobbyWorld;
    }

    /**
     * Get the server version
     * Ex: 1.21.11
     *
     * @since v0.6.5beta
     */
    public static String getServerVersion() {
        return version;
    }

    public static String getLobbyWorld() {
        return lobbyWorld;
    }

    /**
     * Get remote database.
     */
    public static Database getRemoteDatabase() {
        return remoteDatabase;
    }

    public static StatsManager getStatsManager() {
        return statsManager;
    }

    public static com.andrei1058.bedwars.api.BedWars getAPI() {
        return api;
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public static void setParty(Party party) {
        BedWars.party = party;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }
}
