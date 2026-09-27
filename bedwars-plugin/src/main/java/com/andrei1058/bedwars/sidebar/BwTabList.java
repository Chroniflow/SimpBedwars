/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2023 Andrei Dascălu
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

package com.andrei1058.bedwars.sidebar;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.spigot.sidebar.PlayerTab;
import com.andrei1058.spigot.sidebar.Sidebar;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.SidebarLineAnimated;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.andrei1058.bedwars.BedWars.*;

public class BwTabList {

    private static final Comparator<Player> PLAYER_NAME_ORDER = Comparator
            .comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Player::getName)
            .thenComparing(Player::getUniqueId);
    private static final Map<UUID, PlayerListOrderState> managedPlayerListOrders = new HashMap<>();
    private static boolean playerListOrderUpdateScheduled;

    // Player list container. Used to manipulate deployed player tab: lines ecc.
    // Key is player uuid.
    private final HashMap<UUID, PlayerTab> deployedPerPlayerTabList = new HashMap<>();
    private final BwSidebar sidebar;

    public BwTabList(BwSidebar sidebar) {
        this.sidebar = sidebar;
    }

    /**
     * Triggered when sidebar context changes.
     * Arena/ game state change.
     */
    void handlePlayerList() {

        handleHealthIcon();
        requestPlayerListOrderUpdate();
        synchronizePlayerListVisibility(null, null);
        boolean fullFormatting = !this.isTabFormattingDisabled();
        if (!fullFormatting && sidebar.getArena() == null) {
            clearDeployedTabs();
            return;
        }

        LinkedHashMap<UUID, Player> desiredPlayers = new LinkedHashMap<>();
        if (null == sidebar.getArena()) {
            // if tab formatting is enabled in lobby world
            if (config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_LOBBY) &&
                    !config.getLobbyWorldName().trim().isEmpty()) {

                World lobby = Bukkit.getWorld(config.getLobbyWorldName());
                if (null == lobby) {
                    clearDeployedTabs();
                    return;
                }
                lobby.getPlayers().forEach(inLobby -> desiredPlayers.put(inLobby.getUniqueId(), inLobby));
            }
            // sometimes due to timing issues player is not listed yet in lobby players
            desiredPlayers.put(sidebar.getPlayer().getUniqueId(), sidebar.getPlayer());
            synchronizeTabs(desiredPlayers, true);
            return;
        }

        sidebar.getArena().getPlayers().forEach(playing -> desiredPlayers.put(playing.getUniqueId(), playing));
        if (sidebar.getArena().isSpectator(sidebar.getPlayer())) {
            sidebar.getArena().getSpectators()
                    .forEach(spectator -> desiredPlayers.put(spectator.getUniqueId(), spectator));
        }
        synchronizeTabs(desiredPlayers, fullFormatting);
    }

    public void handleHealthIcon() {
        if (null == sidebar.getHandle()) {
            return;
        }

        if (null == sidebar.getArena()) {
            sidebar.getHandle().hidePlayersHealth();
            return;
        } else if (sidebar.getArena().getStatus() != GameState.playing) {
            sidebar.getHandle().hidePlayersHealth();
            return;
        }

        List<String> animation = Language.getList(sidebar.getPlayer(), Messages.FORMATTING_SCOREBOARD_HEALTH);
        if (animation.isEmpty()) return;
        SidebarLine line;
        if (animation.size() > 1) {
            String[] lines = new String[animation.size()];
            for (int i = 0; i < animation.size(); i++) {
                lines[i] = animation.get(i);
            }
            line = new SidebarLineAnimated(lines);
        } else {
            final String text = animation.get(0);
            line = new SidebarLine() {
                @NotNull
                @Override
                public String getLine() {
                    return text;
                }
            };
        }

        if (config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_HEALTH_ENABLE)) {
            sidebar.getHandle().showPlayersHealth(line, config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_HEALTH_IN_TAB));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (null != sidebar.getArena() && null != sidebar.getHandle()) {
                sidebar.getArena().getPlayers().forEach(player -> {
                    if (SidebarHealthPolicy.shouldDisplay(sidebar.getArena().getStatus(), false)) {
                        sidebar.getHandle().setPlayerHealth(player, (int) Math.ceil(player.getHealth()));
                    } else {
                        sidebar.getHandle().clearPlayerHealth(player);
                    }
                });
                sidebar.getArena().getSpectators().forEach(sidebar.getHandle()::clearPlayerHealth);
            }
        }, 10L);
    }

    /**
     * @return true if tab formatting is disabled for current sidebar/ arena stage
     */
    public boolean isTabFormattingDisabled() {
        if (null == sidebar.getArena()) {

            if (getServerType() == ServerType.SHARED) {
                if (config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_LOBBY) &&
                        !config.getLobbyWorldName().trim().isEmpty()) {

                    World lobby = Bukkit.getWorld(config.getLobbyWorldName());
                    return null == lobby || !sidebar.getPlayer().getWorld().getName().equals(lobby.getName());
                }
            }

            return !config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_LOBBY);
        }
        GameState status = sidebar.getArena().getStatus();

        // if tab formatting is disabled in game
        if (status == GameState.playing) {
            return !config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_PLAYING);
        }

        // if tab formatting is disabled in starting
        if (status == GameState.starting) {
            return !config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_STARTING);
        }

        // if tab formatting is disabled in waiting
        if (status == GameState.waiting) {
            return !config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_WAITING);
        }

        // if tab formatting is disabled in restarting
        return status != GameState.restarting || !config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_RESTARTING);
    }

    /**
     * Handle given player in sidebar owner tab list.
     * Will remove existing tab and give a new one based on game conditions list like spectator, team red, etc.
     * Will handle invisibility potion as well.
     */
    public void giveUpdateTabFormat(@NotNull Player player, boolean skipStateCheck, @Nullable Boolean spectator) {
        // if sidebar was not created
        if (sidebar.getHandle() == null) {
            return;
        }
        requestPlayerListOrderUpdate();
        synchronizePlayerListVisibility(player, spectator);

        // unique tab list name
        String playerTabId = player.getUniqueId().toString();

        if (!skipStateCheck) {
            if (this.isTabFormattingDisabled()) {
                giveUpdateTeamColor(player, spectator);
                return;
            }
        }
        SidebarLine prefix;
        SidebarLine suffix;
        IArena arena = sidebar.getArena();
        Sidebar handle = sidebar.getHandle();

        if (null == arena) {
            prefix = getPlayerRowText(Messages.FORMATTING_SB_TAB_LOBBY_PREFIX, player, null);
            suffix = getPlayerRowText(Messages.FORMATTING_SB_TAB_LOBBY_SUFFIX, player, null);

            PlayerTab tab = handle.playerTabCreate(
                    playerTabId, player, prefix, suffix, PlayerTab.PushingRule.NEVER,
                    this.sidebar.getPlaceholders(player)
            );
            deployTab(tab);
            return;
        }

        // Active players must not receive spectator rows. A spectator viewer
        // receives them so the rows can be placed after every active player.
        if (arena.isSpectator(player) || (spectator != null && spectator)) {
            if (!arena.isSpectator(sidebar.getPlayer())) {
                handle.clearPlayerHealth(player);
                removeDeployedTab(player.getUniqueId());
                return;
            }
            giveUpdateSpectatorTabFormat(player, arena);
            return;
        }

        // this is reached only by alive players
        GameState status = arena.getStatus();
        if (status != GameState.playing) {

            ITeam team = resolvePlayerListTeam(arena, player);
            HashMap<String, String> replacements = getTeamReplacements(team);

            switch (status) {
                case waiting:
                    prefix = getPlayerRowText(Messages.FORMATTING_SB_TAB_WAITING_PREFIX, player, replacements);
                    suffix = getPlayerRowText(Messages.FORMATTING_SB_TAB_WAITING_SUFFIX, player, replacements);
                    break;
                case starting:
                    prefix = getPlayerRowText(Messages.FORMATTING_SB_TAB_STARTING_PREFIX, player, replacements);
                    suffix = getPlayerRowText(Messages.FORMATTING_SB_TAB_STARTING_SUFFIX, player, replacements);
                    break;
                case restarting:
                    prefix = getPlayerRowText(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX, player, replacements);
                    suffix = getPlayerRowText(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_SUFFIX, player, replacements);
                    break;
                default:
                    throw new IllegalStateException("Unhandled game status!");
            }
            ChatColor fallbackColor = team == null ? null : getPlayerListColor(team);
            PlayerTab t = handle.playerTabCreate(
                    playerTabId, player, prefix, suffix, collisionPushingRule(status, false, player),
                    this.sidebar.getPlaceholders(player), fallbackColor == null ? ChatColor.WHITE : fallbackColor,
                    PlayerTab.NameTagVisibility.ALWAYS, PlayerTab.PlayerListMode.ACTUAL,
                    collisionGroup(status, team, player)
            );
            deployTab(t);
            return;
        }

        // if status is playing and player is alive

        ITeam team = resolvePlayerListTeam(arena, player);
        // tab list of playing state
        HashMap<String, String> replacements = getTeamReplacements(team);

        prefix = getPlayerRowText(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX, player, replacements);
        suffix = getPlayerRowText(Messages.FORMATTING_SB_TAB_PLAYING_SUFFIX, player, replacements);
        ChatColor fallbackColor = team == null ? null : getPlayerListColor(team);

        PlayerTab teamTab = handle.playerTabCreate(
                playerTabId,
                player, prefix, suffix, collisionPushingRule(status, false, player),
                this.sidebar.getPlaceholders(player), fallbackColor == null ? ChatColor.WHITE : fallbackColor,
                player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                        ? PlayerTab.NameTagVisibility.NEVER
                        : PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.ACTUAL, collisionGroup(status, team, player)
        );
        teamTab.setItalic(arena.isReSpawning(player));
        deployTab(teamTab);
    }

    private void giveUpdateSpectatorTabFormat(@NotNull Player player, @NotNull IArena arena) {
        Sidebar handle = sidebar.getHandle();
        ITeam team = resolvePlayerListTeam(arena, player);
        HashMap<String, String> replacements = getTeamReplacements(team);
        String prefixPath;
        String suffixPath;
        GameState status = arena.getStatus();
        if (status == GameState.waiting) {
            prefixPath = Messages.FORMATTING_SB_TAB_WAITING_PREFIX_SPEC;
            suffixPath = Messages.FORMATTING_SB_TAB_WAITING_SUFFIX_SPEC;
        } else if (status == GameState.starting) {
            prefixPath = Messages.FORMATTING_SB_TAB_STARTING_PREFIX_SPEC;
            suffixPath = Messages.FORMATTING_SB_TAB_STARTING_SUFFIX_SPEC;
        } else if (status == GameState.playing) {
            boolean eliminated = team != null;
            prefixPath = eliminated
                    ? Messages.FORMATTING_SB_TAB_PLAYING_ELM_PREFIX
                    : Messages.FORMATTING_SB_TAB_PLAYING_SPEC_PREFIX;
            suffixPath = eliminated
                    ? Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX
                    : Messages.FORMATTING_SB_TAB_PLAYING_SPEC_SUFFIX;
        } else if (status == GameState.restarting) {
            boolean eliminated = team != null;
            prefixPath = eliminated
                    ? Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX
                    : Messages.FORMATTING_SB_TAB_RESTARTING_SPEC_PREFIX;
            suffixPath = eliminated
                    ? Messages.FORMATTING_SB_TAB_RESTARTING_ELM_SUFFIX
                    : Messages.FORMATTING_SB_TAB_RESTARTING_SPEC_SUFFIX;
        } else {
            prefixPath = Messages.FORMATTING_SB_TAB_PLAYING_SPEC_PREFIX;
            suffixPath = Messages.FORMATTING_SB_TAB_PLAYING_SPEC_SUFFIX;
        }
        ChatColor fallbackColor = team == null ? ChatColor.WHITE : getPlayerListColor(team);
        PlayerTab tab = handle.playerTabCreate(
                player.getUniqueId().toString(), player,
                getPlayerRowText(prefixPath, player, replacements),
                getPlayerRowText(suffixPath, player, replacements),
                PlayerTab.PushingRule.NEVER,
                sidebar.getPlaceholders(player), fallbackColor,
                PlayerTab.NameTagVisibility.NEVER,
                PlayerTab.PlayerListMode.SPECTATOR, null
        );
        deployTab(tab);
    }

    /** Recreate an existing row so Sidebar sends a forced one-entry update. */
    void replayPlayerListEntry(@NotNull Player player) {
        if (sidebar.getHandle() != null) {
            sidebar.getHandle().replayHiddenPlayerList(sidebar.getPlayer(), player);
        }
        if (!deployedPerPlayerTabList.containsKey(player.getUniqueId())) return;
        giveUpdateTabFormat(player, false, null);
    }

    /** Remove one target from this viewer's deployed TAB state. */
    void removePlayerListEntry(@NotNull UUID playerId) {
        removeDeployedTab(playerId);
        if (sidebar.getHandle() != null) {
            sidebar.getHandle().removeHiddenPlayerListTarget(sidebar.getPlayer(), playerId);
        }
    }

    private void giveUpdateTeamColor(@NotNull Player player, @Nullable Boolean spectatorOverride) {
        IArena arena = sidebar.getArena();
        Sidebar handle = sidebar.getHandle();
        if (arena == null || handle == null) {
            removeDeployedTab(player.getUniqueId());
            return;
        }

        ITeam team = resolvePlayerListTeam(arena, player);
        boolean spectator = arena.isSpectator(player) || Boolean.TRUE.equals(spectatorOverride);
        if (spectator && !arena.isSpectator(sidebar.getPlayer())) {
            removeDeployedTab(player.getUniqueId());
            return;
        }
        PlayerTab.PlayerListMode playerListMode = spectator
                ? PlayerTab.PlayerListMode.SPECTATOR
                : resolveMinimalPlayerListMode(team, false);
        if (playerListMode == null) {
            removeDeployedTab(player.getUniqueId());
            return;
        }
        boolean spectatorRow = playerListMode == PlayerTab.PlayerListMode.SPECTATOR;
        GameState status = sidebar.getArena().getStatus();
        ChatColor fallbackColor = team == null ? null : getPlayerListColor(team);

        PlayerTab tab = handle.playerTabCreate(
                player.getUniqueId().toString(), player, new SidebarLine(), new SidebarLine(),
                collisionPushingRule(sidebar.getArena().getStatus(), spectatorRow, player),
                sidebar.getPlaceholders(player),
                fallbackColor == null ? ChatColor.WHITE : fallbackColor,
                player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                        ? PlayerTab.NameTagVisibility.NEVER
                        : PlayerTab.NameTagVisibility.ALWAYS,
                playerListMode, spectator ? null : collisionGroup(status, team, player)
        );
        tab.setItalic(!spectator && arena.isReSpawning(player));
        deployTab(tab);
    }

    static @Nullable PlayerTab.PlayerListMode resolveMinimalPlayerListMode(
            @Nullable ITeam team, boolean spectator) {
        if (spectator) return null;
        return team == null ? null : PlayerTab.PlayerListMode.ACTUAL;
    }

    static @Nullable ITeam resolvePlayerListTeam(@NotNull IArena arena, @NotNull Player player) {
        ITeam currentTeam = arena.getTeam(player);
        if (currentTeam != null) return currentTeam;
        return arena.getExTeam(player.getUniqueId());
    }

    private void synchronizeTabs(@NotNull Map<UUID, Player> desiredPlayers, boolean fullFormatting) {
        Set<UUID> onlinePlayers = new HashSet<>();
        desiredPlayers.forEach((uuid, player) -> {
            if (player.isOnline()) onlinePlayers.add(uuid);
        });
        List<UUID> stale = deployedPerPlayerTabList.keySet().stream()
                .filter(uuid -> !onlinePlayers.contains(uuid))
                .toList();
        stale.forEach(this::removeDeployedTab);
        desiredPlayers.values().stream()
                .filter(Player::isOnline)
                .forEach(player -> {
                    if (fullFormatting) {
                        giveUpdateTabFormat(player, true, null);
                    } else {
                        giveUpdateTeamColor(player, null);
                    }
                });
    }

    private void clearDeployedTabs() {
        new ArrayList<>(deployedPerPlayerTabList.keySet()).forEach(this::removeDeployedTab);
    }

    private void removeDeployedTab(@NotNull UUID playerId) {
        PlayerTab playerTab = deployedPerPlayerTabList.remove(playerId);
        if (playerTab != null && sidebar.getHandle() != null) {
            sidebar.getHandle().removeTab(playerTab.getIdentifier());
        }
    }

    private void deployTab(@NotNull PlayerTab tab) {
        UUID targetId = tab.getPlayer().getUniqueId();
        deployedPerPlayerTabList.put(targetId, tab);
    }

    void refreshPlayerListState() {
        IArena arena = sidebar.getArena();
        if (arena == null) return;
        synchronizePlayerListVisibility(null, null);
        for (PlayerTab tab : List.copyOf(deployedPerPlayerTabList.values())) {
            Player player = tab.getPlayer();
            if (!player.isOnline()
                    || (arena.isSpectator(player) && !arena.isSpectator(sidebar.getPlayer()))) {
                removeDeployedTab(player.getUniqueId());
                continue;
            }
            tab.setColor(getPlayerListColor(resolvePlayerListTeam(arena, player)));
            tab.setItalic(arena.isReSpawning(player));
        }
    }

    private void synchronizePlayerListVisibility(@Nullable Player newlySpectator,
                                                 @Nullable Boolean spectatorOverride) {
        Sidebar handle = sidebar.getHandle();
        if (handle == null) return;
        IArena arena = sidebar.getArena();
        if (arena == null || arena.isSpectator(sidebar.getPlayer())) {
            handle.synchronizeHiddenPlayerList(sidebar.getPlayer(), List.of());
            return;
        }
        List<Player> hidden = new ArrayList<>(arena.getSpectators());
        if (Boolean.TRUE.equals(spectatorOverride) && newlySpectator != null
                && newlySpectator.isOnline()) {
            hidden.add(newlySpectator);
        }
        handle.synchronizeHiddenPlayerList(sidebar.getPlayer(), hidden);
    }

    @NotNull
    private SidebarLine getPlayerRowText(String path, Player targetPlayer,
                                         @Nullable HashMap<String, String> replacements) {
        List<String> strings = Language.getList(sidebar.getPlayer(), path);
        if (strings.isEmpty()) {
            return new SidebarLine() {
                @NotNull
                @Override
                public String getLine() {
                    return "";
                }
            };
        }

        strings = new ArrayList<>();
        for (String string : Language.getList(sidebar.getPlayer(), path)) {
            String parsed = applyPlayerRowTeamMarkers(string, replacements)
                    .replace("{vPrefix}", BedWars.getChatSupport().getPrefix(targetPlayer))
                    .replace("{vSuffix}", BedWars.getChatSupport().getSuffix(targetPlayer));

            strings.add(parsed);
        }

        if (strings.size() == 1) {
            final String line = strings.get(0);
            return new SidebarLine() {
                @NotNull
                @Override
                public String getLine() {
                    return line;
                }
            };
        }

        final String[] lines = new String[strings.size()];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = strings.get(i);
        }
        return new SidebarLineAnimated(lines);
    }

    static @NotNull String applyPlayerRowTeamMarkers(
            @NotNull String template, @Nullable Map<String, String> replacements) {
        String rendered = template;
        if (replacements == null) {
            // Spectators without a former team still use the same configurable
            // row templates. Never leak unresolved team placeholders into TAB.
            rendered = rendered.replace("{teamName}", "")
                    .replace("{teamColor}", "")
                    .replace("{teamLetter}", "");
        } else {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                rendered = rendered.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return rendered;
    }

    @NotNull HashMap<String, String> getTeamReplacements(@Nullable ITeam team) {
        HashMap<String, String> replacements = new HashMap<>();
        String displayName = null == team ? "" : team.getDisplayName(Language.getPlayerLanguage(sidebar.getPlayer()));
        replacements.put("{teamName}", displayName);
        replacements.put("{teamLetter}", null == team || displayName.isEmpty() ? "" : team.getColor().chat() + (displayName.substring(0, 1)));
        replacements.put("{teamColor}", null == team ? "" : team.getColor().chat().toString());

        return replacements;
    }

    static ChatColor getPlayerListColor(@Nullable ITeam targetTeam) {
        if (targetTeam == null) return ChatColor.WHITE;
        return targetTeam.getColor().chat();
    }

    /** Shared scoreboard collision group for live players on the same arena team. */
    static @Nullable String collisionGroup(@Nullable ITeam team, @NotNull Player player) {
        return collisionGroup(team, false);
    }

    static @Nullable String collisionGroup(@NotNull GameState status, @Nullable ITeam team,
                                           @NotNull Player player) {
        if (status != GameState.playing || team == null) return null;
        return team.getIdentity().toString();
    }

    static @Nullable String collisionGroup(@Nullable ITeam team, boolean invisible) {
        if (team == null) return null;
        return team.getIdentity().toString();
    }

    private static PlayerTab.PushingRule collisionPushingRule(@NotNull GameState status,
                                                               boolean spectator,
                                                               @NotNull Player player) {
        return collisionPushingRule(status, spectator, player.hasPotionEffect(PotionEffectType.INVISIBILITY));
    }

    static PlayerTab.PushingRule collisionPushingRule(@NotNull GameState status,
                                                       boolean spectator,
                                                       boolean invisible) {
        // Live players in an active game join a shared collision team. Waiting
        // and starting players retain normal collision until the round begins.
        return status == GameState.playing && !spectator
                ? PlayerTab.PushingRule.PUSH_OTHER_TEAMS
                : PlayerTab.PushingRule.NEVER;
    }

    /**
     * Returns the arena roster grouped from red to violet. Active players stay
     * in team order; every spectator is appended after all active players.
     */
    static List<Player> orderedArenaPlayers(@NotNull IArena arena) {
        List<ITeam> teams = new ArrayList<>(arena.getTeams());
        teams.sort(TabTeamOrder.COMPARATOR);
        LinkedHashMap<UUID, List<Player>> teamMembers = new LinkedHashMap<>();
        for (ITeam team : teams) {
            teamMembers.put(team.getIdentity(), new ArrayList<>());
        }

        Set<UUID> seenPlayers = new HashSet<>();
        List<Player> unassigned = new ArrayList<>();
        for (Player player : arena.getPlayers()) {
            if (!seenPlayers.add(player.getUniqueId())) continue;
            ITeam team = resolvePlayerListTeam(arena, player);
            List<Player> members = team == null ? null : teamMembers.get(team.getIdentity());
            if (members == null) {
                unassigned.add(player);
            } else {
                members.add(player);
            }
        }
        List<Player> spectators = new ArrayList<>();
        for (Player player : arena.getSpectators()) {
            if (seenPlayers.add(player.getUniqueId())) spectators.add(player);
        }
        spectators.sort(PLAYER_NAME_ORDER);
        List<Player> ordered = new ArrayList<>(seenPlayers.size());
        appendSortedGroups(ordered, teamMembers.values());
        unassigned.sort(PLAYER_NAME_ORDER);
        ordered.addAll(unassigned);
        ordered.addAll(spectators);
        return ordered;
    }

    private static void appendSortedGroups(List<Player> destination, Collection<List<Player>> groups) {
        for (List<Player> group : groups) {
            group.sort(PLAYER_NAME_ORDER);
            destination.addAll(group);
        }
    }

    /**
     * Coalesce the many sidebar updates produced by a join/team/death event into
     * one player-list refresh on the next tick.
     */
    private static void requestPlayerListOrderUpdate() {
        if (playerListOrderUpdateScheduled) return;
        if (plugin == null || !plugin.isEnabled()) {
            applyPlayerListOrder(List.of());
            return;
        }
        playerListOrderUpdateScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            playerListOrderUpdateScheduled = false;
            applyServerPlayerListOrder();
        });
    }

    private static void applyServerPlayerListOrder() {
        LinkedHashMap<UUID, Player> arenaPlayers = new LinkedHashMap<>();
        for (Player player : orderedActiveArenaPlayers(Arena.getArenas())) {
            arenaPlayers.put(player.getUniqueId(), player);
        }

        List<Player> lobbyPlayers = new ArrayList<>();
        if (config.getBoolean(ConfigPath.SB_CONFIG_SIDEBAR_LIST_FORMAT_LOBBY)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (Arena.getArenaByPlayer(player) != null) continue;
                if (getServerType() == ServerType.SHARED && !isInConfiguredLobbyWorld(player)) continue;
                lobbyPlayers.add(player);
            }
        }
        lobbyPlayers.sort(PLAYER_NAME_ORDER);

        List<Player> orderedPlayers = new ArrayList<>(lobbyPlayers.size() + arenaPlayers.size());
        orderedPlayers.addAll(lobbyPlayers);
        orderedPlayers.addAll(arenaPlayers.values());
        applyPlayerListOrder(orderedPlayers);
    }

    /**
     * Builds the global arena section without consulting cosmetic TAB switches.
     * Every arena keeps a minimal scoreboard Team context, so team grouping and
     * spectrum ordering must remain active even when full prefixes are disabled.
     */
    static @NotNull List<Player> orderedActiveArenaPlayers(
            @NotNull Collection<? extends IArena> activeArenas) {
        List<IArena> arenas = new ArrayList<>(activeArenas);
        arenas.sort(Comparator.comparing(IArena::getArenaName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(IArena::getArenaName));

        LinkedHashMap<UUID, Player> activePlayers = new LinkedHashMap<>();
        LinkedHashMap<UUID, Player> spectatorPlayers = new LinkedHashMap<>();
        for (IArena arena : arenas) {
            for (Player player : orderedArenaPlayers(arena)) {
                if (!player.isOnline()) continue;
                LinkedHashMap<UUID, Player> destination = arena.isSpectator(player)
                        ? spectatorPlayers : activePlayers;
                destination.putIfAbsent(player.getUniqueId(), player);
            }
        }
        List<Player> ordered = new ArrayList<>(activePlayers.size() + spectatorPlayers.size());
        ordered.addAll(activePlayers.values());
        ordered.addAll(spectatorPlayers.values());
        return ordered;
    }

    static void applyPlayerListOrder(@NotNull Collection<Player> orderedPlayers) {
        LinkedHashMap<UUID, Player> desiredPlayers = new LinkedHashMap<>();
        orderedPlayers.forEach(player -> desiredPlayers.putIfAbsent(player.getUniqueId(), player));

        Iterator<Map.Entry<UUID, PlayerListOrderState>> staleStates = managedPlayerListOrders.entrySet().iterator();
        while (staleStates.hasNext()) {
            Map.Entry<UUID, PlayerListOrderState> entry = staleStates.next();
            if (desiredPlayers.containsKey(entry.getKey())) continue;
            entry.getValue().restoreIfUnchanged();
            staleStates.remove();
        }

        // The 1.21 client sorts this field descending, so the first desired
        // player must receive the largest value.
        int order = desiredPlayers.size();
        for (Player player : desiredPlayers.values()) {
            PlayerListOrderState state = managedPlayerListOrders.get(player.getUniqueId());
            if (state == null || state.player != player) {
                state = new PlayerListOrderState(player);
                managedPlayerListOrders.put(player.getUniqueId(), state);
            }
            state.apply(order--);
        }
    }

    static void restorePlayerListOrder() {
        playerListOrderUpdateScheduled = false;
        applyPlayerListOrder(List.of());
    }

    private static boolean isInConfiguredLobbyWorld(@NotNull Player player) {
        String lobbyWorldName = config.getLobbyWorldName().trim();
        return !lobbyWorldName.isEmpty() && player.getWorld().getName().equals(lobbyWorldName);
    }

    private static final class PlayerListOrderState {
        private final Player player;
        private final int originalOrder;
        private int appliedOrder;

        private PlayerListOrderState(Player player) {
            this.player = player;
            this.originalOrder = player.getPlayerListOrder();
            this.appliedOrder = originalOrder;
        }

        private void apply(int order) {
            if (player.getPlayerListOrder() != order) player.setPlayerListOrder(order);
            appliedOrder = order;
        }

        private void restoreIfUnchanged() {
            if (player.isOnline() && player.getPlayerListOrder() == appliedOrder
                    && appliedOrder != originalOrder) {
                player.setPlayerListOrder(originalOrder);
            }
        }
    }

    /**
     * Clear tab lines from instance.
     */
    public void onSidebarRemoval() {
        requestPlayerListOrderUpdate();
        sidebar.getHandle().clearLines();
        deployedPerPlayerTabList.clear();
        sidebar.getHandle().removeTabs();
    }
}
