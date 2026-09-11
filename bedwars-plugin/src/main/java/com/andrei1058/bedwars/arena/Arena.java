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

package com.andrei1058.bedwars.arena;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.NextEvent;
import com.andrei1058.bedwars.api.arena.generator.GeneratorType;
import com.andrei1058.bedwars.api.arena.generator.IGenerator;
import com.andrei1058.bedwars.api.arena.shop.ShopHolo;
import com.andrei1058.bedwars.api.arena.stats.*;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.ITeamAssigner;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.events.gameplay.GameEndEvent;
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.andrei1058.bedwars.api.events.gameplay.NextEventChangeEvent;
import com.andrei1058.bedwars.api.events.player.PlayerJoinArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerReJoinEvent;
import com.andrei1058.bedwars.api.events.server.ArenaDisableEvent;
import com.andrei1058.bedwars.api.events.server.ArenaEnableEvent;
import com.andrei1058.bedwars.api.events.server.ArenaRestartEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.region.Region;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.api.tasks.PlayingTask;
import com.andrei1058.bedwars.api.tasks.RestartingTask;
import com.andrei1058.bedwars.api.tasks.StartingTask;
import com.andrei1058.bedwars.arena.stats.GameStatsManager;
import com.andrei1058.bedwars.arena.stats.StatisticsOrdered;
import com.andrei1058.bedwars.arena.tasks.GamePlayingTask;
import com.andrei1058.bedwars.arena.tasks.GameRestartingTask;
import com.andrei1058.bedwars.arena.tasks.GameStartingTask;
import com.andrei1058.bedwars.arena.tasks.ReJoinTask;
import com.andrei1058.bedwars.arena.team.BedWarsTeam;
import com.andrei1058.bedwars.arena.team.TeamAssigner;
import com.andrei1058.bedwars.configuration.ArenaConfig;
import com.andrei1058.bedwars.configuration.Sounds;
import com.andrei1058.bedwars.levels.internal.InternalLevel;
import com.andrei1058.bedwars.levels.internal.PerMinuteTask;
import com.andrei1058.bedwars.listeners.LobbyAnnouncements;
import com.andrei1058.bedwars.listeners.blockstatus.BlockStatusListener;
import com.andrei1058.bedwars.listeners.dropshandler.PlayerDrops;
import com.andrei1058.bedwars.money.internal.MoneyPerMinuteTask;
import com.andrei1058.bedwars.maprestore.internal.WorldNameValidator;
import com.andrei1058.bedwars.shop.ShopCache;
import com.andrei1058.bedwars.sidebar.SidebarService;
import com.andrei1058.bedwars.support.citizens.JoinNPC;
import com.andrei1058.bedwars.support.paper.TeleportManager;
import com.andrei1058.bedwars.support.papi.SupportPAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static com.andrei1058.bedwars.BedWars.*;
import static com.andrei1058.bedwars.api.language.Language.*;
import static com.andrei1058.bedwars.arena.upgrades.BaseListener.isOnABase;

@SuppressWarnings("WeakerAccess")
public class Arena implements IArena {

    private static final HashMap<String, IArena> arenaByName = new HashMap<>();
    private static final HashMap<Player, IArena> arenaByPlayer = new HashMap<>();
    private static final HashMap<String, IArena> arenaByIdentifier = new HashMap<>();
    private static final LinkedList<IArena> arenas = new LinkedList<>();
    private static final Set<String> restoringArenas = ConcurrentHashMap.newKeySet();
    private static int gamesBeforeRestart = config.getInt(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_MODE_GAMES_BEFORE_RESTART);
    public static HashMap<UUID, Integer> afkCheck = new HashMap<>();
    public static HashMap<UUID, Integer> magicMilk = new HashMap<>();


    private List<Player> players = new ArrayList<>();
    private List<Player> spectators = new ArrayList<>();
    private final ArenaSignRegistry signs = new ArenaSignRegistry();
    private GameState status = GameState.restarting;
    private YamlConfiguration yml;
    private ArenaConfig cm;
    private int minPlayers = 2, maxPlayers = 10, maxInTeam = 1, islandRadius = 10;
    public int upgradeDiamondsCount = 0, upgradeEmeraldsCount = 0;
    public boolean allowSpectate = true;
    private World world;
    private String group = ArenaGroupPolicy.DEFAULT_GROUP, arenaName, worldName;
    private List<ITeam> teams = new ArrayList<>();
    private final ArenaTeamParticipation teamParticipation = new ArenaTeamParticipation();
    private final PlacedBlockTracker placedBlocks = new PlacedBlockTracker();
    private List<String> nextEvents = new ArrayList<>();
    private List<Region> regionsList = new ArrayList<>();
    private int renderDistance;
    private boolean destroyed;
    private boolean arenaIndicatorRefreshScheduled;

    private final List<Player> leaving = new ArrayList<>();

    /**
     * Current event, used at scoreboard
     */
    private NextEvent nextEvent = NextEvent.DIAMOND_GENERATOR_TIER_II;
    private int diamondTier = 1, emeraldTier = 1;

    /**
     * Players in respawn session
     */
    private ConcurrentHashMap<Player, Integer> respawnSessions = new ConcurrentHashMap<>();

    /**
     * Invisibility for armor when you drink an invisibility potion
     */
    private ConcurrentHashMap<Player, Integer> showTime = new ConcurrentHashMap<>();

    /**
     * Player location before joining.
     * The player is teleported to this location if the server is running in SHARED mode.
     */
    private static final HashMap<Player, Location> playerLocation = new HashMap<>();

    private final GameStatsManager gameStats = new GameStatsManager(this);


    /* ARENA TASKS */
    private StartingTask startingTask = null;
    private PlayingTask playingTask = null;
    private RestartingTask restartingTask = null;

    /* ARENA GENERATORS */
    private List<IGenerator> oreGenerators = new ArrayList<>();

    private PerMinuteTask perMinuteTask;

    private MoneyPerMinuteTask moneyperMinuteTask;

    private static final LinkedList<IArena> enableQueue = new LinkedList<>();

    private Location respawnLocation, spectatorLocation, waitingLocation;
    private int yKillHeight;
    private Instant startTime;
    private ITeamAssigner teamAssigner = new TeamAssigner();

    private boolean allowMapBreak = false;
    private @Nullable ITeam winner;

    /**
     * Load an arena.
     * This will check if it was set up right.
     *
     * @param name - world name
     * @param p    - This will send messages to the player if something went wrong while loading the arena. Can be NULL.
     */
    public Arena(String name, Player p) {
        if (!WorldNameValidator.isSafe(name)) {
            plugin.getLogger().severe("Refused to load arena with unsafe world name: " + name);
            return;
        }
        if (!autoscale) {
            for (IArena mm : enableQueue) {
                if (mm.getArenaName().equalsIgnoreCase(name)) {
                    plugin.getLogger().severe("Tried to load arena " + name + " but it is already in the enable queue.");
                    if (p != null)
                        p.sendMessage(ChatColor.RED + "竞技场 " + name + " 已在启用队列中。");
                    return;
                }
            }
            if (getArenaByName(name) != null) {
                plugin.getLogger().severe("Tried to load arena " + name + " but it is already enabled.");
                if (p != null)
                    p.sendMessage(ChatColor.RED + "竞技场 " + name + " 已启用。");
                return;
            }
        }
        this.arenaName = name;
        if (autoscale) {
            this.worldName = BedWars.arenaManager.generateGameID();
        } else {
            this.worldName = arenaName;
        }

        cm = new ArenaConfig(BedWars.plugin, name, plugin.getDataFolder().getPath() + "/Arenas");

        yml = cm.getYml();
        if (yml.get("Team") == null) {
            if (p != null) p.sendMessage("§c竞技场尚未设置任何队伍：" + name);
            plugin.getLogger().severe("You didn't set any team for arena: " + name);
            clearRestoring(name);
            return;
        }
        if (yml.getConfigurationSection("Team").getKeys(false).size() < 2) {
            if (p != null) p.sendMessage("§c竞技场至少需要设置 2 个队伍：" + name);
            plugin.getLogger().severe("You must set at least 2 teams on: " + name);
            clearRestoring(name);
            return;
        }
        maxInTeam = Math.max(1, yml.getInt("maxInTeam", 1));
        maxPlayers = yml.getConfigurationSection("Team").getKeys(false).size() * maxInTeam;
        minPlayers = Math.max(ArenaStartPolicy.ABSOLUTE_MINIMUM_PLAYERS, yml.getInt("minPlayers", 2));
        if (minPlayers > maxPlayers) {
            plugin.getLogger().warning("竞技场 " + name + " 的 minPlayers=" + minPlayers
                    + " 超过总容量 " + maxPlayers + "，修正配置前不会自动开始。");
        }
        allowSpectate = yml.getBoolean("allowSpectate");
        islandRadius = yml.getInt(ConfigPath.ARENA_ISLAND_RADIUS);
        allowMapBreak = yml.getBoolean(ConfigPath.ARENA_ALLOW_MAP_BREAK);
        setGroup(ArenaGroupPolicy.resolveConfigured(
                ArenaGroupPolicy.read(yml),
                config.getYml().getStringList(ConfigPath.GENERAL_CONFIGURATION_ARENA_GROUPS)));


        if (!BedWars.getAPI().getRestoreAdapter().isWorld(name)) {
            if (p != null) p.sendMessage(ChatColor.RED + "找不到地图：" + name);
            plugin.getLogger().log(Level.WARNING, "There isn't any map called " + name);
            clearRestoring(name);
            return;
        }

        boolean error = false;
        for (String team : yml.getConfigurationSection("Team").getKeys(false)) {
            String colorS = yml.getString("Team." + team + ".Color");
            if (colorS == null) continue;
            colorS = colorS.toUpperCase();
            try {
                TeamColor.fromName(colorS);
            } catch (Exception e) {
                if (p != null) p.sendMessage("§c队伍 " + team + " 的颜色无效，竞技场：" + name);
                plugin.getLogger().severe("Invalid color at team: " + team + " in arena: " + name);
                error = true;
            }
            for (String stuff : Arrays.asList("Color", "Spawn", "Bed", "Shop", "Upgrade", "Iron", "Gold")) {
                if (yml.get("Team." + team + "." + stuff) == null) {
                    if (p != null) p.sendMessage("§c队伍 " + team + " 尚未设置 " + stuff + "，竞技场：" + name);
                    plugin.getLogger().severe(stuff + " not set for " + team + " team on: " + name);
                    error = true;
                }
            }
        }
        if (yml.get("generator.Diamond") == null) {
            if (p != null) p.sendMessage("§c竞技场尚未设置钻石生成点：" + name);
            plugin.getLogger().severe("There isn't set any Diamond generator on: " + name);
        }
        if (yml.get("generator.Emerald") == null) {
            if (p != null) p.sendMessage("§c竞技场尚未设置绿宝石生成点：" + name);
            plugin.getLogger().severe("There isn't set any Emerald generator on: " + name);
        }
        if (yml.get("waiting.Loc") == null) {
            if (p != null) p.sendMessage("§c竞技场尚未设置等待大厅出生点：" + name);
            plugin.getLogger().severe("Waiting spawn not set on: " + name);
            clearRestoring(name);
            return;
        }
        if (error) {
            clearRestoring(name);
            return;
        }
        yKillHeight = yml.getInt(ConfigPath.ARENA_Y_LEVEL_KILL);
        addToEnableQueue(this);
        Language.saveIfNotExists(Messages.ARENA_DISPLAY_GROUP_PATH + getGroup().toLowerCase(), String.valueOf(getGroup().charAt(0)).toUpperCase() + group.substring(1).toLowerCase());
    }

    /**
     * Use this method when the world was loaded successfully.
     */
    @Override
    public void init(World world) {
        if (!autoscale) {
            if (getArenaByName(arenaName) != null) return;
        }
        this.world = world;
        this.worldName = world.getName();
        getConfig().setName(worldName);
        world.getEntities().stream().filter(e -> e.getType() != EntityType.PLAYER)
                .filter(e -> e.getType() != EntityType.PAINTING).filter(e -> e.getType() != EntityType.ITEM_FRAME)
                .forEach(Entity::remove);
        for (String s : getConfig().getList(ConfigPath.ARENA_GAME_RULES)) {
            String[] rule = s.split(":");
            if (rule.length == 2) GameRules.set(world, rule[0], rule[1]);
        }
        GameRules.enforceArenaEnvironment(world);
        world.setAutoSave(false);

        /* Clear setup armor-stands */
        for (Entity e : world.getEntities()) {
            if (e.getType() == EntityType.ARMOR_STAND) {
                if (!((ArmorStand) e).isVisible()) e.remove();
            }
        }

        //Create teams
        for (String team : yml.getConfigurationSection("Team").getKeys(false)) {
            if (getTeam(team) != null) {
                BedWars.plugin.getLogger().severe("A team with name: " + team + " was already loaded for arena: " + getArenaName());
                continue;
            }
            String teamRoot = "Team." + team + ".";
            Location teamSpawn = configuredPlayerLocation(teamRoot + "Spawn",
                    teamRoot + ConfigPath.ARENA_TEAM_SPAWN_FACING);
            BedWarsTeam bwt = new BedWarsTeam(team, TeamColor.fromName(yml.getString(teamRoot + "Color")), teamSpawn,
                    cm.getArenaLoc("Team." + team + ".Bed"), cm.getArenaLoc("Team." + team + ".Shop"), cm.getArenaLoc("Team." + team + ".Upgrade"), this);
            teams.add(bwt);
            bwt.spawnGenerators();
        }

        //Load diamond/ emerald generators
        Location location;
        for (String type : Arrays.asList("Diamond", "Emerald")) {
            if (yml.get("generator." + type) != null) {
                for (String s : yml.getStringList("generator." + type)) {
                    location = cm.convertStringToArenaLocation(s);
                    if (location == null) {
                        plugin.getLogger().severe("Invalid location for " + type + " generator: " + s);
                        continue;
                    }
                    oreGenerators.add(new OreGenerator(location, this, GeneratorType.valueOf(type.toUpperCase()), null));
                }
            }
        }

        arenas.add(this);
        arenaByName.put(getArenaName(), this);
        arenaByIdentifier.put(worldName, this);
        // Keep the queue identity until the world is indexed. Environment
        // guards must recognize it throughout initialization in SHARED mode.
        removeFromEnableQueue(this);
        clearRestoring(getArenaName());
        world.getWorldBorder().setCenter(cm.getArenaLoc("waiting.Loc"));
        world.getWorldBorder().setSize(yml.getInt("worldBorder"));

        /* Check if lobby removal is set */
        if (!getConfig().getYml().isSet(ConfigPath.ARENA_WAITING_POS1) && getConfig().getYml().isSet(ConfigPath.ARENA_WAITING_POS2)) {
            plugin.getLogger().severe("Lobby Pos1 isn't set! The arena's lobby won't be removed!");
        }
        if (getConfig().getYml().isSet(ConfigPath.ARENA_WAITING_POS1) && !getConfig().getYml().isSet(ConfigPath.ARENA_WAITING_POS2)) {
            plugin.getLogger().severe("Lobby Pos2 isn't set! The arena's lobby won't be removed!");
        }

        /* Register arena signs */
        registerSigns();
        //Call event
        Bukkit.getPluginManager().callEvent(new ArenaEnableEvent(this));

        // Re Spawn Session Location
        respawnLocation = configuredPlayerLocation(ConfigPath.ARENA_SPEC_LOC, ConfigPath.ARENA_SPEC_FACING);
        if (respawnLocation == null) {
            respawnLocation = configuredPlayerLocation("waiting.Loc", ConfigPath.ARENA_WAITING_FACING);
        }
        if (respawnLocation == null) {
            respawnLocation = world.getSpawnLocation();
        }
        //

        // Spectator location
        spectatorLocation = configuredPlayerLocation(ConfigPath.ARENA_SPEC_LOC, ConfigPath.ARENA_SPEC_FACING);
        if (spectatorLocation == null) {
            spectatorLocation = configuredPlayerLocation("waiting.Loc", ConfigPath.ARENA_WAITING_FACING);
        }
        if (spectatorLocation == null) {
            spectatorLocation = world.getSpawnLocation();
        }
        //

        // Waiting location
        waitingLocation = configuredPlayerLocation("waiting.Loc", ConfigPath.ARENA_WAITING_FACING);
        if (waitingLocation == null) {
            waitingLocation = world.getSpawnLocation();
        }
        //

        changeStatus(GameState.waiting);

        //
        for (NextEvent ne : NextEvent.values()) {
            nextEvents.add(ne.toString());
        }

        upgradeDiamondsCount = getGeneratorsCfg().getInt(getGeneratorsCfg().getYml().get(getGroup() + "." + ConfigPath.GENERATOR_DIAMOND_TIER_II_START) == null ?
                "Default." + ConfigPath.GENERATOR_DIAMOND_TIER_II_START : getGroup() + "." + ConfigPath.GENERATOR_DIAMOND_TIER_II_START);
        upgradeEmeraldsCount = getGeneratorsCfg().getInt(getGeneratorsCfg().getYml().get(getGroup() + "." + ConfigPath.GENERATOR_EMERALD_TIER_II_START) == null ?
                "Default." + ConfigPath.GENERATOR_EMERALD_TIER_II_START : getGroup() + "." + ConfigPath.GENERATOR_EMERALD_TIER_II_START);
        plugin.getLogger().info("Load done: " + getArenaName());


        // entity tracking range - player
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("spigot.yml"));
        renderDistance = yaml.get("world-settings." + getWorldName() + ".entity-tracking-range.players") == null ?
                yaml.getInt("world-settings.default.entity-tracking-range.players") : yaml.getInt("world-settings." + getWorldName() + ".entity-tracking-range.players");
    }

    /**
     * Add a player to the arena
     *
     * @param p              - Player to add.
     * @param skipOwnerCheck - True if you want to skip the party checking for this player. This
     * @return true if was added.
     */
    public boolean addPlayer(Player p, boolean skipOwnerCheck) {
        if (p == null) return false;
        debug("Player added: " + p.getName() + " arena: " + getArenaName());
        /* used for base enter/leave event */
        isOnABase.remove(p);
        //
        if (getArenaByPlayer(p) != null) {
            return false;
        }
        if (getParty().hasParty(p)) {
            if (!skipOwnerCheck) {
                if (!getParty().isOwner(p)) {
                    p.sendMessage(getMsg(p, Messages.COMMAND_JOIN_DENIED_NOT_PARTY_LEADER));
                    return false;
                }
                int partySize = (int) getParty().getMembers(p).stream().filter(member -> {
                    IArena arena = Arena.getArenaByPlayer(member);
                    if (arena == null) {
                        return true;
                    }
                    return arena.isSpectator(member);
                }).count();

                if (partySize > maxInTeam * getTeams().size() - getPlayers().size()) {
                    p.sendMessage(getMsg(p, Messages.COMMAND_JOIN_DENIED_PARTY_TOO_BIG));
                    return false;
                }
                for (Player mem : getParty().getMembers(p)) {
                    if (mem == p) continue;
                    IArena a = Arena.getArenaByPlayer(mem);
                    if (a != null) {
                        /*if (a.isPlayer(mem)) {
                            a.removePlayer(mem, false);
                        } else */
                        if (a.isSpectator(mem)) {
                            a.removeSpectator(mem, false);
                        }
                    }
                    addPlayer(mem, true);
                }
            }
        }

        ArenaDepartureGuard.restore(leaving, p);

        if (status == GameState.waiting || (status == GameState.starting && (startingTask != null && startingTask.getCountdown() > 1))) {
            if (players.size() >= maxPlayers && !isVip(p)) {
                TextComponent text = new TextComponent(getMsg(p, Messages.COMMAND_JOIN_DENIED_IS_FULL));
                text.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, config.getYml().getString("storeLink")));
                p.spigot().sendMessage(text);
                return false;
            } else if (players.size() >= maxPlayers && isVip(p)) {
                boolean canJoin = false;
                for (Player on : new ArrayList<>(players)) {
                    if (!isVip(on)) {
                        canJoin = true;
                        removePlayer(on, false);
                        TextComponent vipKick = new TextComponent(getMsg(p, Messages.ARENA_JOIN_VIP_KICK));
                        vipKick.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, config.getYml().getString("storeLink")));
                        p.spigot().sendMessage(vipKick);
                        break;
                    }
                }
                if (!canJoin) {
                    p.sendMessage(getMsg(p, Messages.COMMAND_JOIN_DENIED_IS_FULL_OF_VIPS));
                    return false;
                }
            }

            PlayerJoinArenaEvent ev = new PlayerJoinArenaEvent(this, p, false);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;

            //Remove from ReJoin
            ReJoin rejoin = ReJoin.getPlayer(p);
            if (rejoin != null) {
                rejoin.destroy(true);
            }

            p.closeInventory();
            players.add(p);
            setArenaByPlayer(p, this);
            LobbyAnnouncements.playerEnteredArena(p);
            PlayerMotion.disableFlight(p);
            p.setHealth(20);
            broadcastArenaJoin(p);

            /* check if you can start the arena */
            boolean isStatusChange = reevaluateStartEligibility();
            shortenStartCountdownWhenFull();

            //half full arena time shorten
            if (players.size() >= getMaxPlayers() / 2 && players.size() > minPlayers) {
                if (startingTask != null) {
                    if (Bukkit.getScheduler().isCurrentlyRunning(startingTask.getTask())) {
                        if (startingTask.getCountdown() > getConfig().getInt(ConfigPath.GENERAL_CONFIGURATION_START_COUNTDOWN_HALF)) {
                            startingTask.setCountdown(BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_START_COUNTDOWN_HALF));
                        }
                    }
                }
            }

            /* save player inventory etc */
            if (getServerType() != ServerType.BUNGEE) {
                new PlayerGoods(p, true);
                playerLocation.put(p, p.getLocation());
            }
            TeleportManager.teleportC(p, getWaitingLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);

            if (!isStatusChange) {
                SidebarService.getInstance().giveSidebar(p, this, false);
            }
            sendPreGameCommandItems(p);
            p.sendMessage(ChatColor.GOLD + "[选队] " + ChatColor.YELLOW
                    + "使用 /" + mainCmd + " team 打开游戏队伍选择 GUI。"
                    + (getMaxInTeam() > 1 ? " 邀请固定队友可使用 /" + mainCmd + " team squad。" : ""));
            for (PotionEffect pf : p.getActivePotionEffects()) {
                p.removePotionEffect(pf.getType());
            }
        } else if (status == GameState.playing) {
            addSpectator(p, false, null);
            /* stop code if status playing*/
            return false;
        }

        p.getInventory().setArmorContents(new ItemStack[4]);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (getServerType() == ServerType.BUNGEE) {
                BedWars.nms.sendPlayerSpawnPackets(p, this);
            }
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (on == null) continue;
                if (on.equals(p)) continue;
                if (isPlayer(on)) {
                    BedWars.nms.spigotShowPlayer(p, on);
                    BedWars.nms.spigotShowPlayer(on, p);
                } else {
                    BedWars.nms.spigotHidePlayer(p, on);
                    BedWars.nms.spigotHidePlayer(on, p);
                }
            }

            if (getServerType() == ServerType.BUNGEE) {
                BedWars.nms.sendPlayerSpawnPackets(p, this);
            }
        }, 17L);

        if (getServerType() == ServerType.BUNGEE) {
            p.getEnderChest().clear();
        }

        refreshSigns();
        JoinNPC.updateNPCs(getGroup());
        return true;
    }

    /**
     * Add a player as Spectator
     *
     * @param p            Player to be added
     * @param playerBefore True if the player has played in this arena before and he died so now should be a spectator.
     */
    public boolean addSpectator(@NotNull Player p, boolean playerBefore, Location staffTeleport) {
        if (allowSpectate || playerBefore || staffTeleport != null) {
            debug("Spectator added: " + p.getName() + " arena: " + getArenaName());

            if (!playerBefore) {
                PlayerJoinArenaEvent ev = new PlayerJoinArenaEvent(this, p, true);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) return false;
            }

            //Remove from ReJoin
            ReJoin reJoin = ReJoin.getPlayer(p);
            if (reJoin != null) {
                reJoin.destroy(true);
            }

            ArenaDepartureGuard.restore(leaving, p);

            p.closeInventory();
            InvisibilityManager.remove(this, p);
            spectators.add(p);
            players.remove(p);

            updateSpectatorCollideRule(p, false);

            if (!playerBefore) {
                /* save player inv etc if isn't saved yet*/
                if (getServerType() != ServerType.BUNGEE) {
                    new PlayerGoods(p, true);
                    playerLocation.put(p, p.getLocation());
                }
                setArenaByPlayer(p, this);
                LobbyAnnouncements.playerEnteredArena(p);
                broadcastArenaJoin(p);
            }

            SidebarService sidebarService = SidebarService.getInstance();
            if (!playerBefore) sidebarService.giveSidebar(p, this, false);
            nms.setCollide(p, this, false);

            if (!playerBefore) {
                if (staffTeleport == null) {
                    TeleportManager.teleportC(p, getSpectatorLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else {
                    TeleportManager.teleportC(p, staffTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }

            p.setGameMode(GameMode.ADVENTURE);
            applySpectatorInvisibility(p);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isCurrentSpectator(p)) return;
                PlayerMotion.enableFlight(p);
            }, 5L);

            if (p.getPassenger() != null && p.getPassenger().getType() == EntityType.ARMOR_STAND)
                p.getPassenger().remove();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isCurrentSpectator(p)) return;
                if (playerBefore) {
                    sidebarService.giveSidebar(p, this, false);
                    sidebarService.handleElimination(this, p);
                }
                synchronizeSpectatorVisibility(p, !playerBefore);

                if (!playerBefore) {
                    if (staffTeleport == null) {
                        TeleportManager.teleportC(p, getSpectatorLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    } else {
                        TeleportManager.teleport(p, staffTeleport);
                    }
                } else {
                    TeleportManager.teleport(p, getSpectatorLocation());
                }

                PlayerMotion.enableFlight(p);

                /* Spectator items */
                sendSpectatorCommandItems(p);

                p.getInventory().setArmorContents(new ItemStack[4]);
                if (playerBefore) {
                    requestArenaIndicatorRefresh();
                } else {
                    refreshSpectatorHolograms(p);
                }
            });

            ArenaDepartureGuard.restore(leaving, p);

            p.sendMessage(getMsg(p, Messages.COMMAND_JOIN_SPECTATOR_MSG).replace("{arena}", this.getDisplayName()));

        } else {
            p.sendMessage(getMsg(p, Messages.COMMAND_JOIN_SPECTATOR_DENIED_MSG));
            return false;
        }

        if (!playerBefore) refreshArenaIndicators();
        return true;
    }

    private boolean isCurrentSpectator(Player player) {
        return player.isOnline() && spectators.contains(player)
                && getArenaByPlayer(player) == this && !ArenaDepartureGuard.contains(leaving, player);
    }

    /** 将淘汰后的数据包更新集中到下一 tick，避免阻塞 PlayerRespawnEvent。 */
    private void synchronizeSpectatorVisibility(Player spectator, boolean includeOutsidePlayers) {
        for (Player otherSpectator : spectators) {
            if (otherSpectator == spectator || !otherSpectator.isOnline()) continue;
            BedWars.nms.spigotShowPlayer(spectator, otherSpectator);
            BedWars.nms.spigotShowPlayer(otherSpectator, spectator);
        }
        for (Player activePlayer : players) {
            if (!activePlayer.isOnline()) continue;
            BedWars.nms.spigotHidePlayer(spectator, activePlayer);
            BedWars.nms.spigotShowPlayer(activePlayer, spectator);
        }
        if (!includeOutsidePlayers) return;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == spectator || spectators.contains(online) || players.contains(online)) continue;
            BedWars.nms.spigotHidePlayer(spectator, online);
            BedWars.nms.spigotHidePlayer(online, spectator);
        }
    }

    private void refreshSpectatorHolograms(Player spectator) {
        String iso = Language.getPlayerLanguage(spectator).getIso();
        for (IGenerator generator : oreGenerators) generator.updateHolograms(spectator, iso);
        for (ITeam team : teams) {
            for (IGenerator generator : team.getGenerators()) generator.updateHolograms(spectator, iso);
        }
        for (ShopHolo shopHologram : ShopHolo.getShopHolo()) {
            if (shopHologram.getA() == this) shopHologram.updateForPlayer(spectator, iso);
        }
    }

    private void refreshArenaIndicators() {
        refreshSigns();
        JoinNPC.updateNPCs(group);
    }

    /**
     * Join signs and NPC counters are not part of the player's respawn state.
     * Move them out of the spectator packet batch and collapse repeated arena
     * population changes into one update.
     */
    private void requestArenaIndicatorRefresh() {
        if (destroyed || arenaIndicatorRefreshScheduled) return;
        arenaIndicatorRefreshScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            arenaIndicatorRefreshScheduled = false;
            if (!destroyed) refreshArenaIndicators();
        });
    }

    /**
     * Remove a player from the arena
     *
     * @param p          Player to be removed
     * @param disconnect True if the player was disconnected
     */
    public void removePlayer(@NotNull Player p, boolean disconnect) {
        if (!ArenaDepartureGuard.tryBegin(leaving, p)) return;
        debug("Player removed: " + p.getName() + " arena: " + getArenaName());
        respawnSessions.remove(p);

        ITeam team = null;

        Arena.afkCheck.remove(p.getUniqueId());
        BedWars.getAPI().getAFKUtil().setPlayerAFK(p, false);

        InvisibilityManager.remove(this, p);

        if (status == GameState.playing) {
            for (ITeam t : getTeams()) {
                if (t.isMember(p)) {
                    team = t;
                    t.getMembers().remove(p);
                    //noinspection deprecation
                    t.destroyBedHolo(p);
                }
            }
        }

        List<ShopCache.CachedItem> cacheList = new ArrayList<>();
        if (ShopCache.getShopCache(p.getUniqueId()) != null) {
            //noinspection ConstantConditions
            cacheList = ShopCache.getShopCache(p.getUniqueId()).getCachedPermanents();
        }

        if (status == GameState.playing && disconnect && !BedWars.isShuttingDown()) {
            new ReJoin(p, this, team, cacheList);
        }

        LastHit lastHit = LastHit.getLastHit(p);
        Player lastDamager = (lastHit == null) ? null :
                (lastHit.getDamager() instanceof Player) ? (Player) lastHit.getDamager() : null;
        if (lastHit != null) {
            // accept damager in last 13 seconds only.
            if (lastHit.getTime() < System.currentTimeMillis() - 13_000) {
                lastDamager = null;
            }
        }
        Bukkit.getPluginManager().callEvent(new PlayerLeaveArenaEvent(p, this, lastDamager));
        //players.remove must be under call event in order to check if the player is a spectator or not
        players.remove(p);
        removeArenaByPlayer(p, this);

        for (PotionEffect pf : p.getActivePotionEffects()) {
            p.removePotionEffect(pf.getType());
        }

        if (p.getPassenger() != null && p.getPassenger().getType() == EntityType.ARMOR_STAND) p.getPassenger().remove();

        boolean singleTeamDebugStart = startingTask != null && startingTask.isSingleTeamDebugStart();
        if (status == GameState.starting && !singleTeamDebugStart) {
            reevaluateStartEligibility();
        }
        if (status == GameState.playing) {
            BedWars.debug("removePlayer debug1");
            int alive_teams = 0;
            for (ITeam t : getTeams()) {
                if (t == null) continue;
                if (!t.getMembers().isEmpty() || ReJoin.hasPendingForTeam(t)) {
                    alive_teams++;
                }
            }
            if (alive_teams == 1 && !BedWars.isShuttingDown()) {
                checkWinner();
                if (team != null) {
                    if (!team.isBedDestroyed()) {
                        for (Player p2 : this.getPlayers()) {
                            p2.sendMessage(getMsg(p2, Messages.TEAM_ELIMINATED_CHAT).replace("{TeamColor}", team.getColor().chat().toString())
                                    .replace("{TeamName}", team.getDisplayName(Language.getPlayerLanguage(p2))));
                        }
                        for (Player p2 : this.getSpectators()) {
                            p2.sendMessage(getMsg(p2, Messages.TEAM_ELIMINATED_CHAT).replace("{TeamColor}", team.getColor().chat().toString())
                                    .replace("{TeamName}", team.getDisplayName(Language.getPlayerLanguage(p2))));
                        }
                    }
                }
            } else if (alive_teams == 0 && !BedWars.isShuttingDown()) {
                Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> changeStatus(GameState.restarting), 10L);
            }

            // pvp log out
            if (team != null) {
                ITeam killerTeam = getTeam(lastDamager);
                if (lastDamager != null && isPlayer(lastDamager) && killerTeam != null) {
                    String message;
                    PlayerKillEvent.PlayerKillCause cause;
                    if (team.isBedDestroyed()) {
                        cause = PlayerKillEvent.PlayerKillCause.PLAYER_DISCONNECT_FINAL;
                        message = Messages.PLAYER_DIE_PVP_LOG_OUT_FINAL;
                    } else {
                        message = Messages.PLAYER_DIE_PVP_LOG_OUT_REGULAR;
                        cause = PlayerKillEvent.PlayerKillCause.PLAYER_DISCONNECT;
                    }

                    PlayerKillEvent event = new PlayerKillEvent(this, p, team, lastDamager, killerTeam,
                            player -> Language.getMsg(player, message), cause
                    );
                    Bukkit.getPluginManager().callEvent(event);

                    if (null != event.getMessage()) {
                        for (Player inGame : getPlayers()) {
                            Language lang = Language.getPlayerLanguage(inGame);
                            inGame.sendMessage(event.getMessage().apply(inGame)
                                    .replace("{PlayerTeamName}", team.getDisplayName(lang))
                                    .replace("{PlayerColor}", team.getColor().chat().toString()).replace("{PlayerName}", p.getDisplayName())
                                    .replace("{KillerColor}", killerTeam.getColor().chat().toString())
                                    .replace("{KillerName}", lastDamager.getDisplayName())
                                    .replace("{KillerTeamName}", killerTeam.getDisplayName(lang)));
                        }
                    }

                    if (null != event.getMessage()) {
                        for (Player inGame : getSpectators()) {
                            Language lang = Language.getPlayerLanguage(inGame);
                            inGame.sendMessage(event.getMessage().apply(inGame)
                                    .replace("{PlayerTeamName}", team.getDisplayName(lang))
                                    .replace("{PlayerColor}", team.getColor().chat().toString()).replace("{PlayerName}", p.getDisplayName())
                                    .replace("{KillerColor}", killerTeam.getColor().chat().toString())
                                    .replace("{KillerName}", lastDamager.getDisplayName())
                                    .replace("{KillerTeamName}", killerTeam.getDisplayName(lang)));
                        }
                    }
                    PlayerDrops.handlePlayerDrops(this, p, lastDamager, team, killerTeam, cause, new ArrayList<>(Arrays.asList(p.getInventory().getContents())));
                }
            }
        }
        for (Player on : getPlayers()) {
            on.sendMessage(
                    getMsg(on, Messages.COMMAND_LEAVE_MSG)
                            .replace("{vPrefix}", getChatSupport().getPrefix(p))
                            .replace("{vSuffix}", getChatSupport().getSuffix(p))
                            .replace("{playername}", p.getName())
                            .replace("{player}", p.getDisplayName()
                            )
            );
        }
        for (Player on : getSpectators()) {
            on.sendMessage(getMsg(on, Messages.COMMAND_LEAVE_MSG).replace("{vPrefix}", getChatSupport().getPrefix(p)).replace("{playername}", p.getName()).replace("{player}", p.getDisplayName()));
        }

        if (getServerType() == ServerType.SHARED) {
            SidebarService.getInstance().remove(p);
            this.sendToMainLobby(p);

        } else if (getServerType() == ServerType.BUNGEE) {
            Misc.moveToLobbyOrKick(p, this, true);
            return;
        } else {
            this.sendToMainLobby(p);
        }

        /* restore player inventory */
        PlayerGoods pg = PlayerGoods.getPlayerGoods(p);
        if (pg != null) pg.restore();
        playerLocation.remove(p);
        for (PotionEffect pf : p.getActivePotionEffects()) {
            p.removePotionEffect(pf.getType());
        }

        if (!BedWars.isShuttingDown()) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (!ArenaTransitionPolicy.shouldApplyLobbyTransition(
                        disconnect, p.isOnline(), getArenaByPlayer(p) != null)) return;
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (on.equals(p)) continue;
                    if (getArenaByPlayer(on) == null) {
                        BedWars.nms.spigotShowPlayer(p, on);
                        BedWars.nms.spigotShowPlayer(on, p);
                    } else {
                        BedWars.nms.spigotHidePlayer(p, on);
                        BedWars.nms.spigotHidePlayer(on, p);
                    }
                }
                SidebarService.getInstance().giveSidebar(p, null, false);
            }, 5L);
        }

        /* Remove also the party */
        if (getParty().hasParty(p)) {
            if (getParty().isOwner(p)) {
                if (status != GameState.restarting) {
                    if (getParty().isInternal()) {
                        for (Player mem : new ArrayList<>(getParty().getMembers(p))) {
                            mem.sendMessage(getMsg(mem, Messages.ARENA_LEAVE_PARTY_DISBANDED));
                        }
                    }
                    getParty().disband(p);

                }
            }
        }
        PlayerMotion.disableFlight(p);

        //Remove from ReJoin if game ended
        if (status == GameState.restarting) {
            if (ReJoin.exists(p)) {
                //noinspection ConstantConditions
                if (ReJoin.getPlayer(p).getArena() == this) {
                    //noinspection ConstantConditions
                    ReJoin.getPlayer(p).destroy(false);
                }
            }
        }

        //Remove from magic milk
        if (magicMilk.containsKey(p.getUniqueId())) {
            int taskId = magicMilk.remove(p.getUniqueId());
            if (taskId > 0) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
        }

        refreshSigns();
        JoinNPC.updateNPCs(getGroup());

        // fix #340
        // remove player from party if leaves and the owner is still in the arena while waiting or starting
        if (status == GameState.waiting || status == GameState.starting) {
            if (BedWars.getParty().hasParty(p) && !BedWars.getParty().isOwner(p)) {
                for (Player pl : BedWars.getParty().getMembers(p)) {
                    if (BedWars.getParty().isOwner(pl) && pl.getWorld().getName().equalsIgnoreCase(getArenaName())) {
                        BedWars.getParty().removeFromParty(p);
                        break;
                    }
                }
            }
        }

        if (lastHit != null) {
            lastHit.remove();
        }
    }

    /**
     * Remove a spectator from the arena
     *
     * @param p          Player to be removed
     * @param disconnect True if the player was disconnected
     */
    public void removeSpectator(@NotNull Player p, boolean disconnect) {
        debug("Spectator removed: " + p.getName() + " arena: " + getArenaName());

        if (!ArenaDepartureGuard.tryBegin(leaving, p)) return;

        Bukkit.getPluginManager().callEvent(new PlayerLeaveArenaEvent(p, this, null));
        spectators.remove(p);
        removeArenaByPlayer(p, this);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        nms.setCollide(p, this, true);

        Arena.afkCheck.remove(p.getUniqueId());
        BedWars.getAPI().getAFKUtil().setPlayerAFK(p, false);

        if (getServerType() == ServerType.SHARED) {
            SidebarService.getInstance().remove(p);
            this.sendToMainLobby(p);
        } else if (getServerType() == ServerType.MULTIARENA) {
            this.sendToMainLobby(p);

        }
        for (PotionEffect pf : p.getActivePotionEffects()) {
            p.removePotionEffect(pf.getType());
        }

        /* restore player inventory */
        PlayerGoods pg = PlayerGoods.getPlayerGoods(p);
        if (pg != null) pg.restore();
        if (getServerType() == ServerType.BUNGEE) {
            Misc.moveToLobbyOrKick(p, this, true);
            return;
        }
        playerLocation.remove(p);

        if (!BedWars.isShuttingDown()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ArenaTransitionPolicy.shouldApplyLobbyTransition(
                        disconnect, p.isOnline(), getArenaByPlayer(p) != null)) return;
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (on.equals(p)) continue;
                    if (getArenaByPlayer(on) == null) {
                        BedWars.nms.spigotShowPlayer(p, on);
                        BedWars.nms.spigotShowPlayer(on, p);
                    } else {
                        BedWars.nms.spigotHidePlayer(p, on);
                        BedWars.nms.spigotHidePlayer(on, p);
                    }
                }
                SidebarService.getInstance().giveSidebar(p, null, false);
            });
        }

        /* Remove also the party */
        if (getParty().hasParty(p)) {
            if (getParty().isOwner(p)) {
                if (status != GameState.restarting) {
                    if (getParty().isInternal()) {
                        for (Player mem : new ArrayList<>(getParty().getMembers(p))) {
                            mem.sendMessage(getMsg(mem, Messages.ARENA_LEAVE_PARTY_DISBANDED));
                        }
                    }
                    getParty().disband(p);
                }
            }
        }

        PlayerMotion.disableFlight(p);

        //Remove from ReJoin if game ended
        if (ReJoin.exists(p)) {
            //noinspection ConstantConditions
            if (ReJoin.getPlayer(p).getArena() == this) {
                //noinspection ConstantConditions
                ReJoin.getPlayer(p).destroy(false);
            }
        }

        //Remove from magic milk
        if (magicMilk.containsKey(p.getUniqueId())) {
            int taskId = magicMilk.get(p.getUniqueId());
            if (taskId > 0) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
        }

        refreshSigns();
        JoinNPC.updateNPCs(getGroup());
    }

    /**
     * Rejoin an arena
     */
    public boolean reJoin(Player p) {
        ReJoin reJoin = ReJoin.getPlayer(p);
        if (reJoin == null) return false;
        if (reJoin.getArena() != this) return false;
        if (!reJoin.canReJoin()) return false;

        PlayerReJoinEvent ev = new PlayerReJoinEvent(p, this, BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_RE_SPAWN_COUNTDOWN));
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;

        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.equals(p)) continue;
            if (!isInArena(on)) {
                BedWars.nms.spigotHidePlayer(on, p);
                BedWars.nms.spigotHidePlayer(p, on);
            }
        }

        p.closeInventory();
        // The previous Player instance remains equal by UUID after reconnecting.
        // End that departure lifecycle immediately before restoring arena
        // membership so a later quit cannot be swallowed by the guard.
        ArenaDepartureGuard.restore(leaving, p);
        players.add(p);
        for (Player on : players) {
            on.sendMessage(getMsg(on, Messages.COMMAND_REJOIN_PLAYER_RECONNECTED).replace("{playername}", p.getName()).replace("{player}", p.getDisplayName()).replace("{on}", String.valueOf(getPlayers().size())).replace("{max}", String.valueOf(getMaxPlayers())));
        }
        for (Player on : spectators) {
            on.sendMessage(getMsg(on, Messages.COMMAND_REJOIN_PLAYER_RECONNECTED).replace("{playername}", p.getName()).replace("{player}", p.getDisplayName()).replace("{on}", String.valueOf(getPlayers().size())).replace("{max}", String.valueOf(getMaxPlayers())));
        }
        setArenaByPlayer(p, this);
        /* save player inventory etc */
        if (BedWars.getServerType() != ServerType.BUNGEE) {
            // no need to backup inventory because it's empty
            //new PlayerGoods(p, true, true);
            playerLocation.put(p, p.getLocation());
        }
        TeleportManager.teleportC(p, getSpectatorLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        p.getInventory().clear();

        //restore items before re-spawning in team
        ShopCache sc = ShopCache.getShopCache(p.getUniqueId());
        if (sc != null) sc.destroy();
        sc = new ShopCache(p.getUniqueId());
        for (ShopCache.CachedItem ci : reJoin.getPermanentsAndNonDowngradables()) {
            sc.getCachedItems().add(ci);
        }

        reJoin.getBwt().reJoin(p, ev.getRespawnTime());
        reJoin.destroy(false);

        // PlayerReJoinEvent is cancellable and therefore fires before the
        // player is restored to the arena/team. Update other viewers only now,
        // when team color resolution can no longer fall back to white.
        SidebarService.getInstance().handleReJoin(this, p);

        checkWinner();

        SidebarService.getInstance().giveSidebar(p, this, true);
        return true;
    }

    /**
     * Disable the arena.
     * This will automatically kick/ remove the people from the arena.
     */
    public void disable() {
        for (Player p : new ArrayList<>(players)) {
            removePlayer(p, false);
        }
        for (Player p : new ArrayList<>(spectators)) {
            removeSpectator(p, false);
        }
        if (getRestartingTask() != null) getRestartingTask().cancel();
        if (getStartingTask() != null) getStartingTask().cancel();
        if (getPlayingTask() != null) getPlayingTask().cancel();
        plugin.getComponentLogger().info(Component.text("正在卸载竞技场：" + getArenaName(), NamedTextColor.YELLOW));
        for (Player inWorld : getWorld().getPlayers()) {
            inWorld.kickPlayer("服务器正在卸载竞技场。");
        }
        BedWars.getAPI().getRestoreAdapter().onDisable(this);
        Bukkit.getPluginManager().callEvent(new ArenaDisableEvent(getArenaName(), getWorldName()));
        destroyData();
    }

    /**
     * Restart the arena.
     */
    public void restart() {
        if (!getWorld().getPlayers().isEmpty()) {
            plugin.getLogger().warning("竞技场 " + getArenaName() + " 中仍有玩家，已拒绝卸载世界以避免踢出玩家。");
            return;
        }
        if (getRestartingTask() != null) getRestartingTask().cancel();
        if (getStartingTask() != null) getStartingTask().cancel();
        if (getPlayingTask() != null) getPlayingTask().cancel();
        if (null != moneyperMinuteTask) {
            moneyperMinuteTask.cancel();
        }
        if (null != perMinuteTask) {
            perMinuteTask.cancel();
        }
        plugin.getLogger().log(Level.FINE, "Restarting arena: " + getArenaName());
        Bukkit.getPluginManager().callEvent(new ArenaRestartEvent(getArenaName(), getWorldName()));
        BedWars.getAPI().getRestoreAdapter().onRestart(this);
        destroyData();
    }

    /**
     * Restart only after every player has left the arena world.
     *
     * @return true when restart was started, false while evacuation is incomplete
     */
    public boolean restartIfEmpty() {
        if (!getWorld().getPlayers().isEmpty()) return false;
        restart();
        return true;
    }

    //GETTER METHODS

    /**
     * Get the arena world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get the max number of teammates in a team
     */

    @Override
    public int getMaxInTeam() {
        return maxInTeam;
    }

    @Override
    public int getMinPlayers() {
        return minPlayers;
    }

    @Deprecated
    @Override
    public int getMinInTeam() {
        return 1;
    }

    /** Re-check normal start eligibility after players or pre-game squads change. */
    public boolean reevaluateStartEligibility() {
        if (status != GameState.waiting && status != GameState.starting) return false;
        if (startingTask != null && startingTask.isSingleTeamDebugStart()) return false;

        boolean eligible = TeamAssigner.canFormValidTeams(this);
        if (status == GameState.waiting && eligible) {
            changeStatus(GameState.starting);
            return true;
        }
        if (status == GameState.starting && !eligible) {
            changeStatus(GameState.waiting);
            for (Player player : players) {
                player.sendMessage(getMsg(player, Messages.ARENA_START_COUNTDOWN_STOPPED_INSUFF_PLAYERS_CHAT));
            }
            return true;
        }
        return false;
    }

    private void shortenStartCountdownWhenFull() {
        if (status != GameState.starting || startingTask == null) return;
        int shortened = ArenaStartPolicy.shortenCountdownWhenFull(
                players.size(), maxPlayers, startingTask.getCountdown());
        if (shortened != startingTask.getCountdown()) startingTask.setCountdown(shortened);
    }

    /**
     * Get an arena by arena name
     *
     * @param arenaName arena name
     */
    public static IArena getArenaByName(String arenaName) {
        return arenaByName.get(arenaName);
    }

    /**
     * Get an arena by world name
     *
     * @param worldName world name
     */
    public static IArena getArenaByIdentifier(String worldName) {
        return arenaByIdentifier.get(worldName);
    }

    /**
     * Get an arena by a player. Spectator or Player.
     *
     * @param p Target player
     * @return The arena where the player is in. Can be NULL.
     */
    public static IArena getArenaByPlayer(Player p) {
        return arenaByPlayer.get(p);
    }

    /**
     * Get an arenas list
     */
    public static LinkedList<IArena> getArenas() {
        return arenas;
    }

    /**
     * Check whether an arena instance is still part of the active registry.
     */
    public static boolean isRegistered(IArena arena) {
        return arena != null && arenaByName.get(arena.getArenaName()) == arena;
    }

    /**
     * Get the display status for an arena.
     * A message that can be used on signs etc.
     */
    public String getDisplayStatus(Language lang) {
        String s = "";
        switch (status) {
            case waiting:
                s = lang.m(Messages.ARENA_STATUS_WAITING_NAME);
                break;
            case starting:
                s = lang.m(Messages.ARENA_STATUS_STARTING_NAME);
                break;
            case restarting:
                s = lang.m(Messages.ARENA_STATUS_RESTARTING_NAME);
                break;
            case playing:
                s = lang.m(Messages.ARENA_STATUS_PLAYING_NAME);
                break;
        }
        return s.replace("{full}", this.getPlayers().size() == this.getMaxPlayers() ? lang.m(Messages.MEANING_FULL) : "");
    }

    @Override
    public String getDisplayGroup(Player player) {
        return getPlayerLanguage(player).m(Messages.ARENA_DISPLAY_GROUP_PATH + getGroup().toLowerCase());
    }

    @Override
    public String getDisplayGroup(@NotNull Language language) {
        return language.m(Messages.ARENA_DISPLAY_GROUP_PATH + getGroup().toLowerCase());
    }

    /**
     * Get the players list
     */
    @Override
    public List<Player> getPlayers() {
        return players;
    }

    /**
     * Get the max number of players that can play on this arena.
     */
    @Override
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Get the arena name as a message that can be used on signs etc.
     *
     * @return A string with - and _ replaced by a space.
     */
    @Override
    public String getDisplayName() {
        return getConfig().getYml().getString(ConfigPath.ARENA_DISPLAY_NAME, (Character.toUpperCase(arenaName.charAt(0)) + arenaName.substring(1)).replace("_", " ").replace("-", " ")).trim().isEmpty() ?
                (Character.toUpperCase(arenaName.charAt(0)) + arenaName.substring(1)).replace("_", " ").replace("-", " ")
                : getConfig().getString(ConfigPath.ARENA_DISPLAY_NAME);
    }

    @Override
    public void setWorldName(String name) {
        this.worldName = name;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    @Deprecated
    public List<String> getGroups() {
        return List.of(group);
    }

    @Override
    public boolean isInGroup(String group) {
        return ArenaGroupPolicy.matches(this.group, group);
    }

    @Override
    public String getArenaName() {
        return arenaName;
    }

    @Override
    public List<ITeam> getTeams() {
        return teams;
    }

    @Override
    public List<ITeam> getActiveTeamsAtGameStart() {
        return teamParticipation.activeTeams(teams);
    }

    @Override
    public int getTeamSizeAtGameStart(ITeam team) {
        return teamParticipation.gameStartSize(team);
    }

    @Override
    public ArenaConfig getConfig() {
        return cm;
    }

    @Override
    public void addPlacedBlock(Block block) {
        if (!isArenaWorld(block)) return;
        placedBlocks.add(block.getX(), block.getY(), block.getZ());
    }

    @Override
    public void removePlacedBlock(Block block) {
        if (!isArenaWorld(block)) return;
        placedBlocks.remove(block.getX(), block.getY(), block.getZ());
    }

    @Override
    public boolean isBlockPlaced(Block block) {
        return isArenaWorld(block) && placedBlocks.contains(block.getX(), block.getY(), block.getZ());
    }

    private boolean isArenaWorld(Block block) {
        return block != null && worldName != null
                && block.getWorld().getName().equalsIgnoreCase(worldName);
    }

    /**
     * Get a player kills count.
     *
     * @param player     Target player
     * @param finalKills True if you want to get the Final Kills. False for regular kills.
     */
    @Deprecated(forRemoval = true)
    public int getPlayerKills(Player player, boolean finalKills) {
        if (null == player || null == getStatsHolder()) {
            return 0;
        }

        Optional<GameStatistic<?>> st = getStatsHolder().get(player).flatMap(stats ->
                stats.getStatistic(finalKills ? DefaultStatistics.KILLS_FINAL : DefaultStatistics.BEDS_DESTROYED)
        );

        if (st.isEmpty()) {
            return 0;
        }

        GameStatistic<?> gs = st.get();
        return gs instanceof Incrementable ? (int) gs.getValue() : 0;
    }

    /**
     * Get the player beds destroyed count
     *
     * @param player Target player
     */
    @Deprecated(forRemoval = true)
    public int getPlayerBedsDestroyed(Player player) {
        if (null == player || null == getStatsHolder()) {
            return 0;
        }

        Optional<GameStatistic<?>> st = getStatsHolder().get(player)
                .flatMap(stats -> stats.getStatistic(DefaultStatistics.BEDS_DESTROYED));

        if (st.isEmpty()) {
            return 0;
        }

        GameStatistic<?> gs = st.get();
        return gs instanceof Incrementable ? (int) gs.getValue() : 0;
    }

    /**
     * Get the join signs for this arena
     *
     * @return signs.
     */
    public List<Block> getSigns() {
        return signs;
    }

    /** Registered sign blocks whose coordinates are inside the supplied chunk. */
    public List<Block> getSignsInChunk(@NotNull Chunk chunk) {
        return signs.inChunk(chunk);
    }

    /** Sign blocks which may be attached to a block in this chunk. */
    public List<Block> getSignsNearChunk(@NotNull Chunk chunk) {
        return signs.inChunkAndNeighbors(chunk);
    }

    /**
     * Get the island radius
     */
    public int getIslandRadius() {
        return islandRadius;
    }

    //SETTER METHODS
    @Override
    public void setGroup(String group) {
        this.group = ArenaGroupPolicy.normalize(group);
    }

    /** @deprecated 每个竞技场只属于一个组；仅为 2.13.x 附属插件保留。 */
    @Deprecated
    @Override
    public void setGroups(List<String> groups) {
        setGroup(ArenaGroupPolicy.first(groups));
    }

    public static void setArenaByPlayer(Player p, IArena arena) {
        arenaByPlayer.put(p, arena);
        arena.refreshSigns();
        JoinNPC.updateNPCs(arena.getGroup());
    }

    public static void setArenaByName(IArena arena) {
        arenaByName.put(arena.getArenaName(), arena);
    }

    public static void removeArenaByName(@NotNull String arena) {
        arenaByName.remove(arena.replace("_clone", ""));
    }

    public static void removeArenaByPlayer(Player p, @NotNull IArena arena) {
        arenaByPlayer.remove(p);
        arena.refreshSigns();
        JoinNPC.updateNPCs(arena.getGroup());
    }

    /**
     * Set game status without starting stats.
     */
    public void setStatus(GameState status) {
        if (this.status != GameState.playing && status == GameState.playing) {
            startTime = Instant.now();
        }
        updateActiveTeamSnapshot(status);
        // if countdown cancelled
        if (this.status == GameState.starting && status == GameState.waiting) {
            for (Player player : getPlayers()) {
                Language playerLang = Language.getPlayerLanguage(player);
                nms.sendTitle(player, playerLang.m(Messages.ARENA_STATUS_START_COUNTDOWN_CANCELLED_TITLE), playerLang.m(Messages.ARENA_STATUS_START_COUNTDOWN_CANCELLED_SUB_TITLE), 0, 40, 10);
            }
        }
        this.status = status;
    }

    /**
     * Change game status starting tasks.
     */
    public void changeStatus(GameState status) {

        // prevent called twice #https://github.com/andrei1058/BedWars1058/issues/774
        if (status == this.status) {
            return;
        }

        if (this.status != GameState.playing && status == GameState.playing) {
            startTime = Instant.now();
        }
        updateActiveTeamSnapshot(status);
        this.status = status;
        if (status == GameState.restarting) {
            RestartingPlayerState.prepare(this);
        }
        Bukkit.getPluginManager().callEvent(new GameStateChangeEvent(this, status, status));
        refreshSigns();
        if (status == GameState.playing) {
            for (Player p : players) {
                Arena.afkCheck.remove(p.getUniqueId());
                BedWars.getAPI().getAFKUtil().setPlayerAFK(p, false);
            }
            for (Player p : spectators) {
                Arena.afkCheck.remove(p.getUniqueId());
                BedWars.getAPI().getAFKUtil().setPlayerAFK(p, false);
            }

            // Initialize game stats
            getPlayers().forEach(gameStats::init);
        }

        //Stop active tasks to prevent issues
        BukkitScheduler bs = Bukkit.getScheduler();
        if (startingTask != null) {
            if (bs.isCurrentlyRunning(startingTask.getTask()) || bs.isQueued(startingTask.getTask()))
                startingTask.cancel();
        }
        startingTask = null;

        if (playingTask != null) {
            if (bs.isCurrentlyRunning(playingTask.getTask()) || bs.isQueued(playingTask.getTask()))
                playingTask.cancel();
        }
        playingTask = null;

        if (restartingTask != null) {
            if (bs.isCurrentlyRunning(restartingTask.getTask()) || bs.isQueued(restartingTask.getTask()))
                restartingTask.cancel();
        }
        restartingTask = null;
        if (null != moneyperMinuteTask) {
            moneyperMinuteTask.cancel();
        }
        if (null != perMinuteTask) {
            perMinuteTask.cancel();
        }

        players.forEach(c -> SidebarService.getInstance().giveSidebar(c, this, false));

        spectators.forEach(c -> SidebarService.getInstance().giveSidebar(c, this, false));

        if (status == GameState.starting) {
            startingTask = new GameStartingTask(this);
        } else if (status == GameState.playing) {
            if (BedWars.getLevelSupport() instanceof InternalLevel) {
                perMinuteTask = new PerMinuteTask(this);
            }
            moneyperMinuteTask = new MoneyPerMinuteTask(this);
            playingTask = new GamePlayingTask(this);
        } else if (status == GameState.restarting) {
            restartingTask = new GameRestartingTask(this);
        }
    }

    /**
     * Check if a player has vip perms
     */
    public static boolean isVip(Player p) {
        return p.hasPermission(mainCmd + ".*") || p.hasPermission(mainCmd + ".vip");
    }

    /**
     * Check if a player is playing.
     */
    @Override
    public boolean isPlayer(Player p) {
        return players.contains(p);
    }

    /**
     * Check if a player is spectating.
     */
    @Override
    public boolean isSpectator(Player p) {
        return spectators.contains(p);
    }

    @Override
    public boolean isSpectator(UUID player) {
        for (Player p : getSpectators()) {
            if (p.getUniqueId().equals(player)) return true;
        }
        return false;
    }

    @Override
    public boolean isReSpawning(UUID player) {
        if (player == null) return false;
        for (Player reSpawnSession : respawnSessions.keySet()) {
            if (reSpawnSession.getUniqueId().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a join sign for the arena.
     */
    public void addSign(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Block block = loc.getBlock();
        if (signs.contains(block)) return;
        if (BlockStatusListener.canReadSign(block, null) && !(block.getState() instanceof Sign)) return;
        signs.add(block);
        refreshSigns();
        BlockStatusListener.updateBlock(this);
    }

    /**
     * Get game stage.
     */
    @Override
    public GameState getStatus() {
        return status;
    }


    /**
     * Refresh signs.
     */
    public synchronized void refreshSigns() {
        refreshSigns(null);
    }

    /** Refresh only signs in a chunk which Paper has already loaded. */
    public synchronized void refreshSigns(@Nullable Chunk loadedChunk) {
        List<Block> candidates = loadedChunk == null ? getSigns() : getSignsInChunk(loadedChunk);
        for (Block b : candidates) {
            if (!BlockStatusListener.canReadSign(b, loadedChunk)) continue;
            if (!(b.getState() instanceof Sign s)) continue;
            int line = 0;
            for (String string : BedWars.signs.getList("format")) {
                if (line >= 4) break;
                if (string == null) continue;
                s.setLine(line, string.replace("[on]", String.valueOf(getPlayers().size()))
                        .replace("[max]", String.valueOf(getMaxPlayers())).replace("[arena]", getDisplayName())
                        .replace("[status]", getDisplayStatus(Language.getDefaultLanguage()))
                        .replace("[type]", String.valueOf(getMaxInTeam())));
                line++;
            }
            try {
                s.update(true, false);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "无法刷新竞技场 " + getArenaName() + " 的加入告示牌。", ex);
            }
        }
    }

    /**
     * Get a list of spectators for this arena.
     */
    @Override
    public List<Player> getSpectators() {
        return spectators;
    }

    /**
     * Add a kill point to the game stats.
     */
    @Deprecated(forRemoval = true)
    public void addPlayerKill(Player player, boolean finalKill, Player victim) {
    }

    /**
     * Add a destroyed bed point to the player temp stats.
     */
    @Deprecated(forRemoval = true)
    public void addPlayerBedDestroyed(Player player) {
    }

    /**
     * Apply the mandatory state for a player who has just entered the main lobby.
     * Operators can still change their game mode afterwards; this is intentionally
     * not a periodic enforcement task.
     */
    public static void enterLobby(Player p) {
        LobbyService.enter(p);
    }

    private void broadcastArenaJoin(Player joined) {
        if (!ArenaAnnouncementPolicy.shouldBroadcastJoin(status)) return;

        Set<Player> audience = new LinkedHashSet<>(players);
        audience.addAll(spectators);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (LobbyAnnouncements.isLobbyPlayer(viewer)) audience.add(viewer);
        }
        for (Player viewer : audience) {
            viewer.sendMessage(
                    getMsg(viewer, Messages.COMMAND_JOIN_PLAYER_JOIN_MSG)
                            .replace("{vPrefix}", getChatSupport().getPrefix(joined))
                            .replace("{vSuffix}", getChatSupport().getSuffix(joined))
                            .replace("{playername}", joined.getName())
                            .replace("{player}", joined.getDisplayName())
                            .replace("{arena}", getDisplayName())
                            .replace("{on}", String.valueOf(getPlayers().size()))
                            .replace("{max}", String.valueOf(getMaxPlayers()))
            );
        }
    }

    /**
     * This will give the lobby items to the player.
     * Not used in serverType BUNGEE.
     * The compatibility API clears the inventory before applying the items.
     */
    public static void sendLobbyCommandItems(Player p) {
        LobbyService.sendCommandItems(p);
    }

    /** Internal immediate refresh used after the lobby transition is confirmed. */
    public static void refreshLobbyCommandItems(Player p) {
        LobbyService.refreshCommandItems(p);
    }

    /**
     * This will give the pre-game command Items.
     * This will clear the inventory first.
     */
    public void sendPreGameCommandItems(Player p) {
        if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_PATH) == null) return;
        p.getInventory().clear();

        for (String item : config.getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_PATH).getKeys(false)) {
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_MATERIAL.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_MATERIAL.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_DATA.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_DATA.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_SLOT.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_SLOT.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_ENCHANTED.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_ENCHANTED.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_COMMAND.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_COMMAND.replace("%path%", item) + " is not set!");
                continue;
            }
            String command = config.getYml().getString(
                    ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_COMMAND.replace("%path%", item));
            ItemStack i = Misc.createItem(Material.valueOf(config.getYml().getString(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_MATERIAL.replace("%path%", item))),
                    (byte) config.getInt(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_DATA.replace("%path%", item)),
                    config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_ENCHANTED.replace("%path%", item)),
                    SupportPAPI.getSupportPAPI().replace(p, getMsg(p, Messages.GENERAL_CONFIGURATION_WAITING_ITEMS_NAME.replace("%path%", item))),
                    SupportPAPI.getSupportPAPI().replace(p, getList(p, Messages.GENERAL_CONFIGURATION_WAITING_ITEMS_LORE.replace("%path%", item))),
                    p, "RUNCOMMAND", command);

            i = CommandItemAction.tagReturnItem(i, item, command,
                    BedWars.mainCmd, CommandItemAction.Target.ARENA_LOBBY);

            p.getInventory().setItem(config.getInt(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_SLOT.replace("%path%", item)), i);
        }
    }

    /**
     * This will give the spectator command Items.
     * This will clear the inventory first.
     */
    public void sendSpectatorCommandItems(Player p) {
        if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_PATH) == null) return;
        p.getInventory().clear();

        for (String item : config.getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_PATH).getKeys(false)) {
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_MATERIAL.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_MATERIAL.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_DATA.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_DATA.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_SLOT.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_SLOT.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_ENCHANTED.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_ENCHANTED.replace("%path%", item) + " is not set!");
                continue;
            }
            if (config.getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_COMMAND.replace("%path%", item)) == null) {
                BedWars.plugin.getLogger().severe(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_COMMAND.replace("%path%", item) + " is not set!");
                continue;
            }
            String command = config.getYml().getString(
                    ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_COMMAND.replace("%path%", item));
            ItemStack i = Misc.createItem(Material.valueOf(config.getYml().getString(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_MATERIAL.replace("%path%", item))),
                    (byte) config.getInt(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_DATA.replace("%path%", item)),
                    config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_ENCHANTED.replace("%path%", item)),
                    SupportPAPI.getSupportPAPI().replace(p, getMsg(p, Messages.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_NAME.replace("%path%", item))),
                    SupportPAPI.getSupportPAPI().replace(p, getList(p, Messages.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_LORE.replace("%path%", item))),
                    p, "RUNCOMMAND", command);

            i = CommandItemAction.tagReturnItem(i, item, command,
                    BedWars.mainCmd, CommandItemAction.Target.ARENA_LOBBY);

            p.getInventory().setItem(config.getInt(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_SLOT.replace("%path%", item)), i);
        }
    }

    /**
     * Check if a player is in the arena.
     *
     * @return true if is playing or spectating.
     */
    public static boolean isInArena(Player p) {
        return arenaByPlayer.containsKey(p);
    }

    /**
     * Get team by player.
     * Make sure the player is in this arena first.
     */
    @Override
    public ITeam getTeam(Player p) {
        if (p == null) return null;
        ITeam startingTeam = teamParticipation.gameStartTeam(p.getUniqueId());
        if (startingTeam != null && startingTeam.isMember(p)) return startingTeam;
        for (ITeam t : getTeams()) {
            if (t.isMember(p)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Get ex team by player.
     * Check the team where he played before leaving or losing.
     */
    @Override
    public ITeam getExTeam(UUID p) {
        ITeam startingTeam = teamParticipation.gameStartTeam(p);
        if (startingTeam != null) return startingTeam;
        for (ITeam t : getTeams()) {
            if (t.wasMember(p)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Get arena by player name.
     * Used to get the team for a player that has left the arena.
     * Make sure the player is in this arena first.
     */
    @Deprecated
    public ITeam getPlayerTeam(String playerCache) {
        for (ITeam t : getTeams()) {
            for (Player p : t.getMembersCache()) {
                if (p.getName().equals(playerCache)) return t;
            }
        }
        return null;
    }

    /**
     * Check winner. You can always do that.
     * It will manage the arena restart and the needed stuff.
     */
    public void checkWinner() {
        if (status != GameState.restarting) {
            int max = getTeams().size(), eliminated = 0;
            ITeam remainingTeam = null;
            for (ITeam t : getTeams()) {
                if (t.getMembers().isEmpty() && !ReJoin.hasPendingForTeam(t)) {
                    eliminated++;
                } else {
                    remainingTeam = t;
                }
            }
            if (max - eliminated == 1) {
                if (remainingTeam == null || remainingTeam.getMembers().isEmpty()) {
                    return;
                }
                ITeam winningTeam = remainingTeam;
                winner = winningTeam;
                if (!winningTeam.getMembers().isEmpty()) {
                    for (Player p : winningTeam.getMembers()) {
                        if (!p.isOnline()) continue;
                        p.getInventory().clear();
                    }
                    StringBuilder winners = new StringBuilder();
                    //noinspection deprecation
                    for (Player p : winningTeam.getMembersCache()) {
                        if (!winners.toString().contains(p.getDisplayName())) {
                            winners.append(p.getDisplayName()).append(" ");
                        }
                    }
                    if (winners.toString().endsWith(" ")) {
                        winners = new StringBuilder(winners.substring(0, winners.length() - 1));
                    }

                    StatisticsOrdered topInChat = null;

                    if (null != getStatsHolder()) {
                        topInChat = new StatisticsOrdered(
                                this, getConfig().getGameOverridableString(ConfigPath.GENERAL_GAME_END_CHAT_TOP_STATISTIC)
                        );
                        // hide stats row completely when placeholders cannot be replaced
                        if (getConfig().getGameOverridableBoolean(ConfigPath.GENERAL_GAME_END_CHAT_TOP_HIDE_MISSING)) {
                            topInChat.setBoundsPolicy(StatisticsOrdered.BoundsPolicy.SKIP);
                        }
                    }

                    List<Player> receivers = new ArrayList<>(getPlayers().size() + getSpectators().size());
                    receivers.addAll(getPlayers());
                    receivers.addAll(getSpectators());

                    if (null != topInChat) {
                        StatisticsOrdered.StringParser statParser = topInChat.newParser();

                        for (Player receiver : receivers) {

                            Language playerLang = Language.getPlayerLanguage(receiver);

                            String winnerTeamChat = playerLang.m(Messages.GAME_END_TEAM_WON_CHAT);
                            // check if message disabled
                            if (null != winnerTeamChat && !winnerTeamChat.isBlank()) {
                                receiver.sendMessage(winnerTeamChat.replace("{TeamColor}", winningTeam.getColor().chat().toString())
                                        .replace("{TeamName}", winningTeam.getDisplayName(playerLang)));
                            }

                            if (winningTeam.getMembers().contains(receiver) || winningTeam.wasMember(receiver.getUniqueId())) {
                                nms.sendTitle(receiver, getMsg(receiver, Messages.GAME_END_VICTORY_PLAYER_TITLE), null, 0, 70, 20);
                            } else {
                                nms.sendTitle(receiver, playerLang.m(Messages.GAME_END_GAME_OVER_PLAYER_TITLE), null, 0, 70, 20);
                            }

                            statParser.resetIndex();

                            // check if message is disabled
                            List<String> topChat = getList(receiver, Messages.GAME_END_TOP_PLAYER_CHAT);
                            if (topChat.isEmpty() || topChat.size() == 1 && topChat.get(0).isEmpty()) {
                                continue;
                            }

                            for (String s : topChat) {

                                String msg = statParser.parseString(s, playerLang, playerLang.m(Messages.MEANING_NOBODY));
                                if (null == msg) {
                                    continue;
                                }

                                msg = msg.replace("{winnerFormat}", getMaxInTeam() > 1 ? playerLang.m(Messages.FORMATTING_TEAM_WINNER_FORMAT).replace("{members}", winners.toString()) : playerLang.m(Messages.FORMATTING_SOLO_WINNER_FORMAT).replace("{members}", winners.toString()))
                                        .replace("{TeamColor}", winningTeam.getColor().chat().toString()).replace("{TeamName}", winningTeam.getDisplayName(playerLang));

                                receiver.sendMessage(SupportPAPI.getSupportPAPI().replace(receiver, msg));
                            }

                        }
                    }
                }
                changeStatus(GameState.restarting);

                //Game end event
                List<UUID> winners = new ArrayList<>(), losers = new ArrayList<>(), aliveWinners = new ArrayList<>();
                for (Player p : getPlayers()) {
                    aliveWinners.add(p.getUniqueId());
                }
                if (winner != null) {
                    //noinspection deprecation
                    for (Player p : winner.getMembersCache()) {
                        winners.add(p.getUniqueId());
                    }
                }
                for (ITeam bwt : getTeams()) {
                    if (winner != null) {
                        if (bwt == winner) continue;
                    }
                    //noinspection deprecation
                    for (Player p : bwt.getMembersCache()) {
                        losers.add(p.getUniqueId());
                    }
                }
                Bukkit.getPluginManager().callEvent(new GameEndEvent(this, winners, losers, winner, aliveWinners));

            }
            if (players.isEmpty() && status != GameState.restarting) {
                changeStatus(GameState.restarting);
            }
        }
    }

    /**
     * Add a kill to the player temp stats.
     */
    @Deprecated(forRemoval = true)
    public void addPlayerDeath(Player player) {
    }


    /**
     * Set next event for the arena.
     */
    public void setNextEvent(NextEvent nextEvent) {
        if (this.nextEvent != null) {
            Sounds.playSound(this.nextEvent.getSoundPath(), getPlayers());
            Sounds.playSound(this.nextEvent.getSoundPath(), getSpectators());
        }
        Bukkit.getPluginManager().callEvent(new NextEventChangeEvent(this, nextEvent, this.nextEvent));
        this.nextEvent = nextEvent;
    }

    @Override
    public void updateNextEvent() {

        debug("---");
        debug("updateNextEvent called");
        if (nextEvent == NextEvent.EMERALD_GENERATOR_TIER_II && upgradeEmeraldsCount == 0) {
            // next diamond time < next emerald time
            int next = getGeneratorsCfg().getInt(getGeneratorsCfg().getYml().get(getGroup() + "." + ConfigPath.GENERATOR_EMERALD_TIER_III_START) == null ?
                    "Default." + ConfigPath.GENERATOR_EMERALD_TIER_III_START : getGroup() + "." + ConfigPath.GENERATOR_EMERALD_TIER_III_START);
            if (upgradeDiamondsCount < next && diamondTier == 1) {
                setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_II);
            } else if (upgradeDiamondsCount < next && diamondTier == 2) {
                setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_III);
            } else {
                setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_III);
            }
            upgradeEmeraldsCount = next;
            emeraldTier = 2;
            sendEmeraldsUpgradeMessages();
            for (IGenerator o : getOreGenerators()) {
                if (o.getType() == GeneratorType.EMERALD && o.getBwt() == null) {
                    o.upgrade();
                }
            }
        } else if (nextEvent == NextEvent.DIAMOND_GENERATOR_TIER_II && upgradeDiamondsCount == 0) {
            int next = getGeneratorsCfg().getInt(getGeneratorsCfg().getYml().get(getGroup() + "." + ConfigPath.GENERATOR_DIAMOND_TIER_III_START) == null ?
                    "Default." + ConfigPath.GENERATOR_DIAMOND_TIER_III_START : getGroup() + "." + ConfigPath.GENERATOR_DIAMOND_TIER_III_START);
            if (upgradeEmeraldsCount < next && emeraldTier == 1) {
                setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_II);
            } else if (upgradeEmeraldsCount < next && emeraldTier == 2) {
                setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_III);
            } else {
                setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_III);
            }
            upgradeDiamondsCount = next;
            diamondTier = 2;
            sendDiamondsUpgradeMessages();
            for (IGenerator o : getOreGenerators()) {
                if (o.getType() == GeneratorType.DIAMOND && o.getBwt() == null) {
                    o.upgrade();
                }
            }
        } else if (nextEvent == NextEvent.EMERALD_GENERATOR_TIER_III && upgradeEmeraldsCount == 0) {
            emeraldTier = 3;
            sendEmeraldsUpgradeMessages();
            if (diamondTier == 1 && upgradeDiamondsCount > 0) {
                setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_II);
            } else if (diamondTier == 2 && upgradeDiamondsCount > 0) {
                setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_III);
            } else {
                setNextEvent(NextEvent.BEDS_DESTROY);
            }
            for (IGenerator o : getOreGenerators()) {
                if (o.getType() == GeneratorType.EMERALD && o.getBwt() == null) {
                    o.upgrade();
                }
            }
        } else if (nextEvent == NextEvent.DIAMOND_GENERATOR_TIER_III && upgradeDiamondsCount == 0) {
            diamondTier = 3;
            sendDiamondsUpgradeMessages();
            if (emeraldTier == 1 && upgradeEmeraldsCount > 0) {
                setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_II);
            } else if (emeraldTier == 2 && upgradeEmeraldsCount > 0) {
                setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_III);
            } else {
                setNextEvent(NextEvent.BEDS_DESTROY);
            }
            for (IGenerator o : getOreGenerators()) {
                if (o.getType() == GeneratorType.DIAMOND && o.getBwt() == null) {
                    o.upgrade();
                }
            }
        } else if (nextEvent == NextEvent.BEDS_DESTROY && getPlayingTask().getBedsDestroyCountdown() == 0) {
            setNextEvent(NextEvent.ENDER_DRAGON);
        } else if (nextEvent == NextEvent.ENDER_DRAGON && getPlayingTask().getDragonSpawnCountdown() == 0) {
            setNextEvent(NextEvent.GAME_END);
        }

        debug("---");
        debug(nextEvent.toString());
    }

    /**
     * Get arena by players list.
     */
    public static HashMap<Player, IArena> getArenaByPlayer() {
        return arenaByPlayer;
    }

    /**
     * Get next event.
     */
    public NextEvent getNextEvent() {
        return nextEvent;
    }

    /**
     * Get players count for a group
     */
    public static int getPlayers(@NotNull String group) {
        int i = 0;

        for (IArena arena : getArenas()) {
            if (ArenaGroupPolicy.matches(arena.getGroup(), group)) {
                i += arena.getPlayers().size();
            }
        }

        return i;
    }

    /**
     * Register join-signs for arena
     */
    private void registerSigns() {
        if (getServerType() != ServerType.BUNGEE) {
            if (BedWars.signs.getYml().get("locations") != null) {
                for (String st : BedWars.signs.getYml().getStringList("locations")) {
                    String[] data = st.split(",");
                    if (data[0].equals(getArenaName())) {
                        Location l;
                        try {
                            l = new Location(Bukkit.getWorld(data[6]), Double.parseDouble(data[1]), Double.parseDouble(data[2]), Double.parseDouble(data[3]));
                        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                            plugin.getLogger().severe("Could not load sign at: " + Arrays.toString(data));
                            continue;
                        }
                        if (l.getWorld() != null) {
                            Block sign = l.getBlock();
                            if (!signs.contains(sign)) signs.add(sign);
                        }
                    }
                }
            }
            refreshSigns();
        }
    }

    /**
     * Get a team by name
     */
    public ITeam getTeam(String name) {
        for (ITeam bwt : getTeams()) {
            if (bwt.getName().equals(name)) return bwt;
        }
        return null;
    }

    /**
     * Get respawn sessions.
     */
    @Override
    public ConcurrentHashMap<Player, Integer> getRespawnSessions() {
        return respawnSessions;
    }

    @Override
    public void updateSpectatorCollideRule(Player p, boolean collide) {
//        if (!isSpectator(p)) return;
//        for (BedWarsScoreboard sb : BedWarsScoreboard.getScoreboards().values()) {
//            if (sb.getArena() == this) {
//                sb.updateSpectator(p, collide);
//            }
//        }
    }

    /**
     * Get invisibility for armor
     */
    public ConcurrentHashMap<Player, Integer> getShowTime() {
        return showTime;
    }

    /**
     * Get instance of the starting task.
     */
    public StartingTask getStartingTask() {
        return startingTask;
    }

    /**
     * Get instance of the playing task.
     */
    public PlayingTask getPlayingTask() {
        return playingTask;
    }

    /**
     * Get instance of the game restarting task.
     */
    public RestartingTask getRestartingTask() {
        return restartingTask;
    }

    /**
     * Get arena ore generators Ore Generators.
     */
    public List<IGenerator> getOreGenerators() {
        return oreGenerators;
    }

    /**
     * Add a player to the most filled arena.
     * Check if is the party owner first.
     */
    public static boolean joinRandomArena(Player p) {
        List<IArena> arenas = getSorted(getArenas());

        int amount = getParty().hasParty(p) ? (int) getParty().getMembers(p).stream().filter(member -> {
            IArena arena = Arena.getArenaByPlayer(member);
            if (arena == null) {
                return true;
            }
            return arena.isSpectator(member);
        }).count() : 1;

        for (IArena a : arenas) {
            if (a.getPlayers().size() == a.getMaxPlayers()) continue;
            if (a.getMaxPlayers() - a.getPlayers().size() >= amount && a.addPlayer(p, false)) return true;
        }
        return false;
    }

    public static List<IArena> getSorted(List<IArena> arenas) {
        return ArenaSorting.sorted(arenas);
    }

    /**
     * Add a player to the most filled arena from a group.
     */
    public static boolean joinRandomFromGroup(Player p, @NotNull String group) {

        List<IArena> arenas = getSorted(getArenas());

        int amount = getParty().hasParty(p) ? (int) getParty().getMembers(p).stream().filter(member -> {
            IArena arena = Arena.getArenaByPlayer(member);
            if (arena == null) {
                return true;
            }
            return arena.isSpectator(member);
        }).count() : 1;

        for (IArena a : arenas) {
            if (a.getPlayers().size() == a.getMaxPlayers()) continue;
            if (!ArenaGroupPolicy.matches(a.getGroup(), group)) continue;
            if (a.getMaxPlayers() - a.getPlayers().size() >= amount && a.addPlayer(p, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the list of next events to come.
     * Not ordered.
     */
    public List<String> getNextEvents() {
        return new ArrayList<>(nextEvents);
    }

    /**
     * Get player deaths.
     */
    @Deprecated(forRemoval = true)
    public int getPlayerDeaths(Player player, boolean finalDeaths) {
        if (null == player || null == getStatsHolder()) {
            return 0;
        }

        Optional<GameStatistic<?>> st = getStatsHolder().get(player).flatMap(stats ->
                stats.getStatistic(finalDeaths ? DefaultStatistics.DEATHS_FINAL : DefaultStatistics.DEATHS)
        );

        if (st.isEmpty()) {
            return 0;
        }

        GameStatistic<?> gs = st.get();
        return gs instanceof Incrementable ? (int) gs.getValue() : 0;
    }

    /**
     * Show upgrade announcement to players.
     * Change diamondTier value first.
     */
    public void sendDiamondsUpgradeMessages() {
        for (Player p : getPlayers()) {
            p.sendMessage(getMsg(p, Messages.GENERATOR_UPGRADE_CHAT_ANNOUNCEMENT).replace("{generatorType}",
                    getMsg(p, Messages.GENERATOR_HOLOGRAM_TYPE_DIAMOND)).replace("{tier}", getMsg(p, (diamondTier == 2 ? Messages.FORMATTING_GENERATOR_TIER2 : Messages.FORMATTING_GENERATOR_TIER3))));
        }
        for (Player p : getSpectators()) {
            p.sendMessage(getMsg(p, Messages.GENERATOR_UPGRADE_CHAT_ANNOUNCEMENT).replace("{generatorType}",
                    getMsg(p, Messages.GENERATOR_HOLOGRAM_TYPE_DIAMOND)).replace("{tier}", getMsg(p, (diamondTier == 2 ? Messages.FORMATTING_GENERATOR_TIER2 : Messages.FORMATTING_GENERATOR_TIER3))));
        }
    }

    /**
     * Show upgrade announcement to players.
     * Change emeraldTier value first.
     */
    public void sendEmeraldsUpgradeMessages() {
        for (Player p : getPlayers()) {
            p.sendMessage(getMsg(p, Messages.GENERATOR_UPGRADE_CHAT_ANNOUNCEMENT).replace("{generatorType}",
                    getMsg(p, Messages.GENERATOR_HOLOGRAM_TYPE_EMERALD)).replace("{tier}", getMsg(p, (emeraldTier == 2 ? Messages.FORMATTING_GENERATOR_TIER2 : Messages.FORMATTING_GENERATOR_TIER3))));
        }
        for (Player p : getSpectators()) {
            p.sendMessage(getMsg(p, Messages.GENERATOR_UPGRADE_CHAT_ANNOUNCEMENT).replace("{generatorType}",
                    getMsg(p, Messages.GENERATOR_HOLOGRAM_TYPE_EMERALD)).replace("{tier}", getMsg(p, (emeraldTier == 2 ? Messages.FORMATTING_GENERATOR_TIER2 : Messages.FORMATTING_GENERATOR_TIER3))));
        }
    }


    public static int getGamesBeforeRestart() {
        return gamesBeforeRestart;
    }

    public static void setGamesBeforeRestart(int gamesBeforeRestart) {
        Arena.gamesBeforeRestart = gamesBeforeRestart;
    }

    public List<Region> getRegionsList() {
        return regionsList;
    }

    public LinkedList<Vector> getPlaced() {
        return placedBlocks.snapshot();
    }

    @Override
    public Set<Vector> getPlacedBlocksSnapshot() {
        return placedBlocks.immutableSnapshot();
    }

    public static LinkedList<IArena> getEnableQueue() {
        return enableQueue;
    }

    private final Map<UUID, Long> fireballCooldowns = new HashMap<>();

    public Map<UUID, Long> getFireballCooldowns() {
        return fireballCooldowns;
    }

    public void destroyData() {
        if (destroyed) return;
        destroyed = true;
        arenaIndicatorRefreshScheduled = false;

        if (startingTask != null) startingTask.cancel();
        if (playingTask != null) playingTask.cancel();
        if (restartingTask != null) restartingTask.cancel();
        if (perMinuteTask != null) perMinuteTask.cancel();
        if (moneyperMinuteTask != null) moneyperMinuteTask.cancel();

        destroyReJoins();
        if (worldName != null) arenaByIdentifier.remove(worldName);
        arenas.remove(this);
        enableQueue.remove(this);
        if (SidebarService.getInstance() != null) SidebarService.getInstance().removeArena(this);
        for (ReJoinTask rjt : ReJoinTask.getReJoinTasks()) {
            if (rjt.getArena() == this) {
                rjt.destroy();
            }
        }
        for (Despawnable despawnable : new ArrayList<>(BedWars.nms.getDespawnablesList().values())) {
            if (despawnable.getTeam().getArena() == this) {
                despawnable.destroy();
            }
        }
        arenaByName.remove(arenaName);
        arenaByPlayer.entrySet().removeIf(entry -> entry.getValue() == this);
        for (IGenerator og : new ArrayList<>(oreGenerators)) {
            og.destroyData();
        }
        isOnABase.entrySet().removeIf(entry -> entry.getValue().getArena().equals(this));
        for (ITeam bwt : new ArrayList<>(teams)) {
            bwt.destroyData();
        }
        playerLocation.entrySet().removeIf(entry -> entry.getValue().getWorld() != null
                && entry.getValue().getWorld().getName().equalsIgnoreCase(worldName));

        players.clear();
        spectators.clear();
        signs.clear();
        teams.clear();
        placedBlocks.clear();
        nextEvents.clear();
        regionsList.clear();
        respawnSessions.clear();
        showTime.clear();
        oreGenerators.clear();
        startingTask = null;
        playingTask = null;
        restartingTask = null;
        perMinuteTask = null;
        moneyperMinuteTask = null;
        leaving.clear();
        fireballCooldowns.clear();
        teamParticipation.reset();
    }

    private void updateActiveTeamSnapshot(GameState nextStatus) {
        if (nextStatus == GameState.waiting) {
            teamParticipation.reset();
            return;
        }
        if (nextStatus != GameState.playing || status == GameState.playing) return;
        teamParticipation.capture(teams);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Remove an arena from the enable queue.
     */
    public static void removeFromEnableQueue(IArena a) {
        enableQueue.remove(a);
        if (!enableQueue.isEmpty()) {
            BedWars.getAPI().getRestoreAdapter().onEnable(enableQueue.get(0));
            plugin.getLogger().info("Loading arena: " + enableQueue.get(0).getWorldName());
        }
    }

    public static void addToEnableQueue(IArena a) {
        enableQueue.add(a);
        plugin.getLogger().info("Arena " + a.getWorldName() + " was added to the enable queue.");
        if (enableQueue.size() == 1) {
            BedWars.getAPI().getRestoreAdapter().onEnable(a);
            plugin.getLogger().info("Loading arena: " + a.getWorldName());
        }
    }

    public int getUpgradeDiamondsCount() {
        return upgradeDiamondsCount;
    }

    public int getUpgradeEmeraldsCount() {
        return upgradeEmeraldsCount;
    }

    public void setAllowSpectate(boolean allowSpectate) {
        this.allowSpectate = allowSpectate;
    }

    public boolean isAllowSpectate() {
        return allowSpectate;
    }

    public String getWorldName() {
        return worldName;
    }

    @Override
    public int getRenderDistance() {
        return renderDistance;
    }

    @Override
    public Location getReSpawnLocation() {
        return respawnLocation;
    }

    @Override
    public Location getSpectatorLocation() {
        return spectatorLocation;
    }

    @Override
    public Location getWaitingLocation() {
        return waitingLocation;
    }

    @Override
    public boolean startReSpawnSession(Player player, int seconds) {
        if (status != GameState.playing) {
            if (status == GameState.restarting) {
                RestartingPlayerState.preparePlayer(this, player);
            }
            return false;
        }
        if (respawnSessions.get(player) == null) {
            IArena arena = Arena.getArenaByPlayer(player);
            if (arena == null) {
                return false;
            }
            if (!arena.isPlayer(player)) {
                return false;
            }
            player.getInventory().clear();
            if (seconds > 1) {
                player.setCanPickupItems(false);
                applySpectatorInvisibility(player);
                // BedWars1058 keeps the player's normal game mode during the
                // countdown. The session owns interaction blocking and flight;
                // changing to SPECTATOR here causes an avoidable transition
                // back to SURVIVAL when the countdown ends.
                PlayerMotion.enableFlight(player);
                respawnSessions.put(player, seconds);
                Bukkit.getScheduler().runTask(BedWars.plugin, () -> {
                    if (!player.isOnline() || !respawnSessions.containsKey(player)) return;
                    nms.setCollide(player, this, false);
                    InvisibilityManager.synchronizeViewer(this, player);
                    updateSpectatorCollideRule(player, false);
                });
            } else {
                ITeam team = getTeam(player);
                team.respawnMember(player);
            }
            return true;
        }
        return false;
    }

    /**
     * Death spectators and players waiting to respawn must not be rendered in
     * the arena. Hide particles and the status icon as well to avoid visual
     * noise for the affected player.
     */
    private static void applySpectatorInvisibility(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ), true);
    }

    @Override
    public boolean isReSpawning(Player player) {
        return respawnSessions.containsKey(player);
    }

    // used for auto scale conditions
    public static boolean canAutoScale(String arenaName) {
        if (!autoscale) return true;

        if (Arena.getArenas().isEmpty()) return true;

        for (IArena ar : Arena.getEnableQueue()) {
            if (ar.getArenaName().equalsIgnoreCase(arenaName)) return false;
        }

        if (Arena.getGamesBeforeRestart() != -1 && Arena.getArenas().size() >= Arena.getGamesBeforeRestart())
            return false;

        int activeClones = 0;
        for (IArena ar : Arena.getArenas()) {
            if (ar.getArenaName().equalsIgnoreCase(arenaName)) {
                // clone this arena only if there aren't available arena of the same kind
                GameState status = ar.getStatus();
                if (status == GameState.waiting || status == GameState.starting) return false;
            }
            // count active clones
            if (ar.getArenaName().equals(arenaName)) {
                activeClones++;
            }
        }

        // check amount of active clones
        return config.getInt(ConfigPath.GENERAL_CONFIGURATION_AUTO_SCALE_LIMIT) > activeClones;
    }

    public static void markRestoring(@NotNull String arenaName) {
        restoringArenas.add(arenaName.toLowerCase(Locale.ROOT));
    }

    public static void clearRestoring(@NotNull String arenaName) {
        restoringArenas.remove(arenaName.toLowerCase(Locale.ROOT));
    }

    public static boolean isRestoring(@NotNull String arenaName) {
        String normalized = arenaName.toLowerCase(Locale.ROOT);
        if (restoringArenas.contains(normalized)) return true;
        for (IArena arena : enableQueue) {
            if (arena.getArenaName().equalsIgnoreCase(arenaName)) return true;
        }
        IArena arena = getArenaByName(arenaName);
        return arena != null && arena.getStatus() == GameState.restarting;
    }

    @Override
    public boolean equals(Object obj) {
        return ArenaIdentity.equals(worldName, obj);
    }

    @Override
    public int hashCode() {
        return ArenaIdentity.hashCode(worldName);
    }

    private void destroyReJoins() {
        List<ReJoin> reJoins = new ArrayList<>(ReJoin.getReJoinList());
        for (ReJoin reJoin : reJoins) {
            if (reJoin.getArena() == this) {
                reJoin.destroy(true);
            }
        }
    }

    @Override
    public boolean isProtected(Location location) {
        return Misc.isBuildProtected(location, this);
    }

    @Override
    public void abandonGame(Player player) {
        if (player == null) {
            return;
        }

        ITeam team = getTeams().stream().filter(team1 -> team1.wasMember(player.getUniqueId())).findFirst().orElse(null);
        if (team != null) {
            //noinspection deprecation
            team.getMembersCache().removeIf(cachedPlayer -> cachedPlayer.getUniqueId().equals(player.getUniqueId()));
            ReJoin rejoin = ReJoin.getPlayer(player);
            if (rejoin != null) {
                boolean eliminateTeam = ArenaAbandonPolicy.eliminatesTeam(
                        team.getMembers().size(), ReJoin.hasPendingForTeam(team, rejoin));
                rejoin.destroy(eliminateTeam);
            }
            // destroy(false) intentionally keeps a multi-player team alive and
            // therefore does not check for a winner itself. Always re-evaluate
            // after the abandoned player's reconnect reservation is removed.
            if (status == GameState.playing) checkWinner();
        }
    }

    @Override
    public int getYKillHeight() {
        return yKillHeight;
    }

    @Override
    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public ITeamAssigner getTeamAssigner() {
        return teamAssigner;
    }
        
    @Override
    public int getDiamondTier() {
        return diamondTier;
    }


    @Override
    public int getEmeraldTier() {
        return emeraldTier;
    }

    public int getYHeightLimit() {
        return getConfig().getInt(ConfigPath.ARENA_CONFIGURATION_MAX_BUILD_Y);
    }

    @Override
    public void setTeamAssigner(ITeamAssigner teamAssigner) {
        if (teamAssigner == null) {
            this.teamAssigner = new TeamAssigner();
            plugin.getLogger().info("Using Default team assigner on arena: " + this.getArenaName());
        } else {
            this.teamAssigner = teamAssigner;
            plugin.getLogger().warning("Using " + teamAssigner.getClass().getSimpleName() + " team assigner on arena: " + this.getArenaName());
        }
    }

    @Override
    public List<Player> getLeavingPlayers() {
        return leaving;
    }

    /**
     * Remove player from world.
     * Contains fall-backs.
     */
    private void sendToMainLobby(Player player) {
        if (BedWars.getServerType() == ServerType.SHARED) {
            Location loc = playerLocation.get(player);
            if (loc == null) {
                loc = Bukkit.getWorlds().get(0).getSpawnLocation();
                plugin.getLogger().log(Level.SEVERE, player.getName() + " was teleported to the main world because lobby location is not set!");
            }
            applyLobbyStateAfterTeleport(player,
                    TeleportManager.teleportC(player, loc, PlayerTeleportEvent.TeleportCause.PLUGIN));
        } else if (BedWars.getServerType() == ServerType.MULTIARENA) {
            Location lobby = config.getConfigLoc("lobbyLoc");
            if (lobby == null || lobby.getWorld() == null) {
                lobby = Bukkit.getWorlds().getFirst().getSpawnLocation();
                plugin.getLogger().log(Level.SEVERE, player.getName() + " was teleported to the main world because lobby location is not set!");
            }
            applyLobbyStateAfterTeleport(player, TeleportManager.teleportC(
                    player, lobby, PlayerTeleportEvent.TeleportCause.PLUGIN));
        }
    }

    private static void applyLobbyStateAfterTeleport(Player player, CompletableFuture<Boolean> teleport) {
        teleport.whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ArenaTransitionPolicy.shouldApplyLobbyStateAfterTeleport(
                    error == null && Boolean.TRUE.equals(success),
                    player.isOnline(), LobbyService.isCurrentLobbyPlayer(player))) {
                enterLobby(player);
            }
        }));
    }

    public boolean isAllowMapBreak() {
        return allowMapBreak;
    }

    private Location configuredPlayerLocation(String locationPath, String facingPath) {
        return PlayerFacing.apply(cm.getArenaLoc(locationPath), yml.getString(facingPath));
    }

    public void setAllowMapBreak(boolean allowMapBreak) {
        this.allowMapBreak = allowMapBreak;
    }

    @Override
    public @Nullable ITeam getBedsTeam(@NotNull Location location) {
        if (!location.getWorld().getName().equals(this.worldName)) {
            throw new RuntimeException("Given location is not on this game world.");
        }

        if (!nms.isBed(location.getBlock().getType())) {
            return null;
        }

        for (ITeam team : this.teams) {
            if (team.isBed(location)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public @Nullable ITeam getWinner() {
        return winner;
    }

    @Override
    public boolean isTeamBed(Location location) {
        return null != getBedsTeam(location);
    }

    @Override
    public GameStatsHolder getStatsHolder() {
        return gameStats;
    }
}
