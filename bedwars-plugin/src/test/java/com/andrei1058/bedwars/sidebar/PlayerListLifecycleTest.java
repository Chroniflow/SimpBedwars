package com.andrei1058.bedwars.sidebar;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.spigot.sidebar.PlayerTab;
import com.andrei1058.spigot.sidebar.Sidebar;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerListLifecycleTest {

    @Test
    void refreshUsesCurrentTeamColorAndRespawnStyle() throws ReflectiveOperationException {
        Player player = player("Alice");
        AtomicReference<ITeam> currentTeam = new AtomicReference<>(team("red", TeamColor.RED));
        AtomicBoolean respawning = new AtomicBoolean(true);
        IArena arena = arena(player, currentTeam, respawning, false);
        BwSidebar viewer = mock(BwSidebar.class);
        when(viewer.getArena()).thenReturn(arena);
        BwTabList tabList = new BwTabList(viewer);
        PlayerTab row = new PlayerTab("alice", player);
        deploy(tabList, row);

        tabList.refreshPlayerListState();

        assertEquals(ChatColor.RED, row.getColor());
        assertEquals(true, row.isItalic());

        currentTeam.set(team("blue", TeamColor.BLUE));
        respawning.set(false);
        tabList.refreshPlayerListState();

        assertEquals(ChatColor.BLUE, row.getColor());
        assertEquals(false, row.isItalic());
    }

    @Test
    void refreshRemovesAnEliminatedPlayerRow() throws ReflectiveOperationException {
        Player player = player("Alice");
        AtomicReference<ITeam> currentTeam = new AtomicReference<>(team("red", TeamColor.RED));
        IArena arena = arena(player, currentTeam, new AtomicBoolean(false), true);
        BwSidebar viewer = mock(BwSidebar.class);
        Sidebar handle = mock(Sidebar.class);
        when(viewer.getArena()).thenReturn(arena);
        when(viewer.getHandle()).thenReturn(handle);
        Player activeViewer = player("ActiveViewer");
        when(viewer.getPlayer()).thenReturn(activeViewer);
        BwTabList tabList = new BwTabList(viewer);
        PlayerTab row = new PlayerTab("alice", player);
        deploy(tabList, row);

        tabList.refreshPlayerListState();

        verify(handle).removeTab("alice");
        verify(handle).synchronizeHiddenPlayerList(activeViewer, List.of(player));
    }

    @Test
    void spectatorViewerKeepsEliminatedRowsAndRestoresFullList() throws ReflectiveOperationException {
        Player eliminated = player("Eliminated");
        IArena arena = arena(eliminated, new AtomicReference<>(team("red", TeamColor.RED)),
                new AtomicBoolean(false), true);
        BwSidebar viewer = mock(BwSidebar.class);
        Sidebar handle = mock(Sidebar.class);
        when(viewer.getArena()).thenReturn(arena);
        when(viewer.getHandle()).thenReturn(handle);
        when(viewer.getPlayer()).thenReturn(eliminated);
        BwTabList tabList = new BwTabList(viewer);
        deploy(tabList, new PlayerTab("eliminated", eliminated));

        tabList.refreshPlayerListState();

        verify(handle, never()).removeTab("eliminated");
        verify(handle).synchronizeHiddenPlayerList(eliminated, List.of());
    }

    @Test
    void showEventReappliesHiddenStateEvenWithoutAFormattedRow() {
        BwSidebar sidebar = mock(BwSidebar.class);
        Sidebar handle = mock(Sidebar.class);
        Player viewer = player("ActiveViewer");
        Player spectator = player("Spectator");
        when(sidebar.getHandle()).thenReturn(handle);
        when(sidebar.getPlayer()).thenReturn(viewer);

        new BwTabList(sidebar).replayPlayerListEntry(spectator);

        verify(handle).replayHiddenPlayerList(viewer, spectator);
    }

    @Test
    void leavingRemovesHiddenStateEvenWithoutAFormattedRow() {
        BwSidebar sidebar = mock(BwSidebar.class);
        Sidebar handle = mock(Sidebar.class);
        Player viewer = player("ActiveViewer");
        UUID targetId = UUID.randomUUID();
        when(sidebar.getHandle()).thenReturn(handle);
        when(sidebar.getPlayer()).thenReturn(viewer);

        new BwTabList(sidebar).removePlayerListEntry(targetId);

        verify(handle).removeHiddenPlayerListTarget(viewer, targetId);
    }

    private static IArena arena(Player player, AtomicReference<ITeam> currentTeam,
                                AtomicBoolean respawning, boolean spectator) {
        return (IArena) Proxy.newProxyInstance(
                IArena.class.getClassLoader(), new Class<?>[]{IArena.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTeam" -> currentTeam.get();
                    case "getExTeam" -> null;
                    case "isReSpawning" -> respawning.get();
                    case "isSpectator" -> spectator && args[0] != null
                            && player.getUniqueId().equals(((Player) args[0]).getUniqueId());
                    case "getPlayers" -> List.of(player);
                    case "getSpectators" -> spectator ? List.of(player) : List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ITeam team(String name, TeamColor color) {
        UUID identity = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (ITeam) Proxy.newProxyInstance(
                ITeam.class.getClassLoader(), new Class<?>[]{ITeam.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdentity" -> identity;
                    case "getColor" -> color;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player player(String name) {
        UUID uniqueId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> uniqueId;
                    case "isOnline" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static void deploy(BwTabList tabList, PlayerTab row) throws ReflectiveOperationException {
        var field = BwTabList.class.getDeclaredField("deployedPerPlayerTabList");
        field.setAccessible(true);
        ((Map<UUID, PlayerTab>) field.get(tabList)).put(row.getPlayer().getUniqueId(), row);
    }
}
