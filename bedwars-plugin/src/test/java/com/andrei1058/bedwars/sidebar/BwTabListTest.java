package com.andrei1058.bedwars.sidebar;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.spigot.sidebar.PlayerTab;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BwTabListTest {

    @Test
    void activePlayerNameUsesItsOwnTeamColor() {
        ITeam redTeam = (ITeam) Proxy.newProxyInstance(
                ITeam.class.getClassLoader(),
                new Class<?>[]{ITeam.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getColor")) return TeamColor.RED;
                    throw new UnsupportedOperationException(method.getName());
                }
        );

        assertSame(ChatColor.RED, BwTabList.getPlayerListColor(redTeam));
        assertSame(ChatColor.WHITE, BwTabList.getPlayerListColor(null));
    }

    @Test
    void minimalFormattingRetainsCurrentAndEliminatedTeamColors() {
        ITeam red = team("red", TeamColor.RED);
        Player active = player("Active");
        Player eliminated = player("Eliminated");
        Player spectator = player("Spectator");
        IArena arena = arena(
                List.of(red), List.of(active), List.of(eliminated, spectator),
                Map.of(active.getUniqueId(), red), Map.of(eliminated.getUniqueId(), red)
        );

        assertSame(red, BwTabList.resolvePlayerListTeam(arena, active));
        assertSame(red, BwTabList.resolvePlayerListTeam(arena, eliminated));
        assertSame(ChatColor.RED,
                BwTabList.getPlayerListColor(BwTabList.resolvePlayerListTeam(arena, eliminated)));
        assertEquals(null, BwTabList.resolvePlayerListTeam(arena, spectator));
    }

    @Test
    void minimalFormattingExcludesSpectatorsRegardlessOfTheirFormerTeam() {
        ITeam red = team("red", TeamColor.RED);

        assertSame(PlayerTab.PlayerListMode.ACTUAL,
                BwTabList.resolveMinimalPlayerListMode(red, false));
        assertNull(BwTabList.resolveMinimalPlayerListMode(red, true));
        assertNull(BwTabList.resolveMinimalPlayerListMode(null, true));
        assertEquals(null, BwTabList.resolveMinimalPlayerListMode(null, false));
    }

    @Test
    void collisionGroupsFollowTheRealTeamAndPlayingState() {
        ITeam red = team("red", TeamColor.RED);

        assertEquals(red.getIdentity().toString(), BwTabList.collisionGroup(red, false));
        assertEquals(red.getIdentity().toString(), BwTabList.collisionGroup(red, true));
        assertNull(BwTabList.collisionGroup(null, false));
        Player invisible = player("Invisible");
        assertNull(BwTabList.collisionGroup(GameState.waiting, red, invisible));
        assertEquals(red.getIdentity().toString(),
                BwTabList.collisionGroup(GameState.playing, red, invisible));
        assertEquals(PlayerTab.PushingRule.PUSH_OTHER_TEAMS,
                BwTabList.collisionPushingRule(GameState.playing, false, false));
        assertEquals(PlayerTab.PushingRule.NEVER,
                BwTabList.collisionPushingRule(GameState.waiting, false, false));
        assertEquals(PlayerTab.PushingRule.NEVER,
                BwTabList.collisionPushingRule(GameState.starting, false, false));
        assertEquals(PlayerTab.PushingRule.PUSH_OTHER_TEAMS,
                BwTabList.collisionPushingRule(GameState.playing, false, true));
        assertEquals(PlayerTab.PushingRule.NEVER,
                BwTabList.collisionPushingRule(GameState.playing, true, false));
    }

    @Test
    void playerRowsRenderVisibleTeamIdentityMarkers() {
        Map<String, String> replacements = Map.of(
                "{teamColor}", "\u00a7c",
                "{teamName}", "红队",
                "{teamLetter}", "\u00a7c红");

        assertEquals(
                "\u00a7c[\u00a7c红] 红队 {vPrefix}&7[观察者] {vSuffix}",
                BwTabList.applyPlayerRowTeamMarkers(
                        "{teamColor}[{teamLetter}] {teamName} {vPrefix}&7[观察者] {vSuffix}",
                        replacements)
        );
    }

    @Test
    void spectatorRowsWithoutAFormerTeamClearTeamPlaceholders() {
        assertEquals(
                "[] [观察者] Alice",
                BwTabList.applyPlayerRowTeamMarkers(
                        "{teamColor}{teamName}[{teamLetter}] [观察者] Alice", null)
        );
    }

    @Test
    void waitingAndStartingPlayersRemainUnassignedUntilTheRoundStarts() {
        Player player = player("Unassigned");
        IArena waiting = arena(List.of(), List.of(player), List.of(), Map.of(), Map.of(), GameState.waiting);
        IArena starting = arena(List.of(), List.of(player), List.of(), Map.of(), Map.of(), GameState.starting);

        assertNull(BwTabList.resolvePlayerListTeam(waiting, player));
        assertNull(BwTabList.resolvePlayerListTeam(starting, player));
    }

    @Test
    void arenaPlayersAreGroupedByTeamAndSortedByName() {
        ITeam red = team("red", TeamColor.RED);
        ITeam blue = team("blue", TeamColor.BLUE);
        Player bob = player("Bob");
        Player charlie = player("charlie");
        Player alice = player("Alice");
        Player adam = player("Adam");
        Player aaron = player("Aaron");
        Player viewer = player("Viewer");

        Map<UUID, ITeam> currentTeams = Map.of(
                bob.getUniqueId(), red,
                alice.getUniqueId(), red,
                charlie.getUniqueId(), blue,
                adam.getUniqueId(), blue
        );
        Map<UUID, ITeam> formerTeams = Map.of(aaron.getUniqueId(), red);
        IArena arena = arena(
                List.of(red, blue),
                List.of(charlie, bob, adam, alice),
                List.of(viewer, aaron, bob),
                currentTeams,
                formerTeams
        );

        assertEquals(
                List.of("Alice", "Bob", "Adam", "charlie", "Aaron", "Viewer"),
                BwTabList.orderedArenaPlayers(arena).stream().map(Player::getName).toList()
        );
    }

    @Test
    void teamsFollowVisibleSpectrumRegardlessOfConfigurationOrder() {
        List<TeamColor> configuredOrder = List.of(
                TeamColor.DARK_GRAY, TeamColor.BLUE, TeamColor.PINK, TeamColor.WHITE,
                TeamColor.CYAN, TeamColor.DARK_GREEN, TeamColor.RED, TeamColor.GRAY,
                TeamColor.GREEN, TeamColor.YELLOW
        );
        List<ITeam> teams = configuredOrder.stream()
                .map(color -> team(color.name(), color))
                .toList();
        List<Player> players = teams.stream()
                .map(team -> player(team.getColor().name()))
                .toList();
        Map<UUID, ITeam> assignments = java.util.stream.IntStream.range(0, players.size())
                .boxed()
                .collect(java.util.stream.Collectors.toMap(
                        index -> players.get(index).getUniqueId(),
                        teams::get
                ));

        IArena arena = arena(teams, players, List.of(), assignments, Map.of());

        assertEquals(
                List.of("RED", "YELLOW", "GREEN", "DARK_GREEN", "CYAN",
                        "BLUE", "PINK", "WHITE", "GRAY", "DARK_GRAY"),
                BwTabList.orderedArenaPlayers(arena).stream().map(Player::getName).toList()
        );
    }

    @Test
    void globalPlayerListPlacesSpectatorsAfterActivePlayersAcrossArenas() {
        ITeam red = team("red-global", TeamColor.RED);
        ITeam blue = team("blue-global", TeamColor.BLUE);
        Player activeA = player("ActiveA");
        Player activeB = player("ActiveB");
        Player spectatorA = player("SpectatorA");
        Player spectatorB = player("SpectatorB");
        IArena first = arena("a", List.of(red), List.of(activeA), List.of(spectatorA),
                Map.of(activeA.getUniqueId(), red), Map.of(spectatorA.getUniqueId(), red),
                GameState.playing);
        IArena second = arena("b", List.of(blue), List.of(activeB), List.of(spectatorB),
                Map.of(activeB.getUniqueId(), blue), Map.of(spectatorB.getUniqueId(), blue),
                GameState.playing);

        assertEquals(
                List.of("ActiveA", "ActiveB", "SpectatorA", "SpectatorB"),
                BwTabList.orderedActiveArenaPlayers(List.of(second, first)).stream()
                        .map(Player::getName).toList()
        );
    }

    @Test
    void minimalTeamOrderingIncludesWaitingAndStartingArenas() {
        ITeam red = team("red", TeamColor.RED);
        ITeam blue = team("blue", TeamColor.BLUE);
        Player startingBob = player("StartingBob");
        Player startingAlice = player("StartingAlice");
        Player waitingBlue = player("WaitingAlice");
        Player waitingRed = player("WaitingZed");

        IArena waiting = arena("z-waiting", List.of(blue, red),
                List.of(waitingBlue, waitingRed), List.of(),
                Map.of(waitingBlue.getUniqueId(), blue, waitingRed.getUniqueId(), red),
                Map.of(), GameState.waiting);
        IArena starting = arena("a-starting", List.of(red),
                List.of(startingBob, startingAlice), List.of(),
                Map.of(startingBob.getUniqueId(), red, startingAlice.getUniqueId(), red),
                Map.of(), GameState.starting);

        assertEquals(
                List.of("StartingAlice", "StartingBob", "WaitingZed", "WaitingAlice"),
                BwTabList.orderedActiveArenaPlayers(List.of(waiting, starting)).stream()
                        .map(Player::getName)
                        .toList()
        );
    }

    @Test
    void writesTheCalculatedOrderAndRestoresThePreviousOwner() {
        PlayerOrderState firstState = new PlayerOrderState(2);
        PlayerOrderState secondState = new PlayerOrderState(9);
        Player first = orderedPlayer("Alice", firstState);
        Player second = orderedPlayer("Bob", secondState);

        try {
            BwTabList.applyPlayerListOrder(List.of(first, second));

            assertEquals(2, firstState.order);
            assertEquals(0, firstState.writes.get());
            assertEquals(1, secondState.order);
            assertEquals(1, secondState.writes.get());

            BwTabList.restorePlayerListOrder();

            assertEquals(2, firstState.order);
            assertEquals(0, firstState.writes.get());
            assertEquals(9, secondState.order);
            assertEquals(2, secondState.writes.get());
        } finally {
            BwTabList.applyPlayerListOrder(List.of());
        }
    }

    @Test
    void doesNotOverwriteAnOrderChangedByAnotherPlugin() {
        PlayerOrderState state = new PlayerOrderState(7);
        Player player = orderedPlayer("Alice", state);

        try {
            BwTabList.applyPlayerListOrder(List.of(player));
            assertEquals(1, state.order);

            state.order = 4;
            BwTabList.applyPlayerListOrder(List.of());

            assertEquals(4, state.order);
            assertEquals(1, state.writes.get());
        } finally {
            BwTabList.applyPlayerListOrder(List.of());
        }
    }

    private static ITeam team(String name, TeamColor color) {
        UUID identity = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (ITeam) Proxy.newProxyInstance(
                ITeam.class.getClassLoader(),
                new Class<?>[]{ITeam.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdentity" -> identity;
                    case "getColor" -> color;
                    case "getName" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Player orderedPlayer(String name, PlayerOrderState state) {
        UUID uniqueId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> uniqueId;
                    case "isOnline" -> true;
                    case "getPlayerListOrder" -> state.order;
                    case "setPlayerListOrder" -> {
                        state.order = (int) args[0];
                        state.writes.incrementAndGet();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Player player(String name) {
        UUID uniqueId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> uniqueId;
                    case "isOnline" -> true;
                    case "hasPotionEffect" -> false;
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static IArena arena(List<ITeam> teams, List<Player> players, List<Player> spectators,
                                 Map<UUID, ITeam> currentTeams, Map<UUID, ITeam> formerTeams) {
        return arena(teams, players, spectators, currentTeams, formerTeams, GameState.playing);
    }

    private static IArena arena(List<ITeam> teams, List<Player> players, List<Player> spectators,
                                Map<UUID, ITeam> currentTeams, Map<UUID, ITeam> formerTeams,
                                GameState state) {
        return arena(state.name(), teams, players, spectators, currentTeams, formerTeams, state);
    }

    private static IArena arena(String name, List<ITeam> teams, List<Player> players,
                                List<Player> spectators, Map<UUID, ITeam> currentTeams,
                                Map<UUID, ITeam> formerTeams, GameState state) {
        return (IArena) Proxy.newProxyInstance(
                IArena.class.getClassLoader(),
                new Class<?>[]{IArena.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getArenaName" -> name;
                    case "getTeams" -> teams;
                    case "getPlayers" -> players;
                    case "getSpectators" -> spectators;
                    case "isSpectator" -> spectators.stream().anyMatch(spectator ->
                            spectator.getUniqueId().equals(((Player) args[0]).getUniqueId()));
                    case "getTeam" -> currentTeams.get(((Player) args[0]).getUniqueId());
                    case "getExTeam" -> formerTeams.get((UUID) args[0]);
                    case "getStatus" -> state;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class PlayerOrderState {
        private int order;
        private final AtomicInteger writes = new AtomicInteger();

        private PlayerOrderState(int order) {
            this.order = order;
        }
    }
}
