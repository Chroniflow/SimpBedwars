package com.andrei1058.spigot.sidebar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarTabSynchronizationTest {

    @Test
    void synchronizesHiddenPlayerInfoRowsPerViewer() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player spectator = player("Spectator");

        sidebar.synchronizeHiddenPlayerList(viewer, List.of(spectator));
        sidebar.synchronizeHiddenPlayerList(viewer, List.of(viewer, spectator));
        assertEquals(List.of(spectator), renderer.hidden.stream()
                .map(CapturedTarget::target).toList());

        sidebar.synchronizeHiddenPlayerList(viewer, List.of());
        assertEquals(List.of(spectator), renderer.shown.stream()
                .map(CapturedTarget::target).toList());
    }

    @Test
    void hiddenRowsFollowOwnershipAndAreReleasedOnLeave() {
        SidebarManager manager = SidebarManager.getInstance();
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar previous = sidebar(renderer);
        Sidebar current = sidebar(new RecordingRenderer());
        Player viewer = player("HiddenOwnershipViewer");
        Player spectator = player("HiddenSpectator");
        manager.claimDisplayNameOwnership(previous, viewer);
        try {
            previous.synchronizeHiddenPlayerList(viewer, List.of(spectator));
            manager.claimDisplayNameOwnership(current, viewer);
            assertEquals(1, renderer.shown.size());
            previous.synchronizeHiddenPlayerList(viewer, List.of(spectator));
            assertEquals(1, renderer.hidden.size(), "suspended sidebar must not change visibility");
            manager.releaseDisplayNameOwnership(current, viewer);
            assertEquals(2, renderer.hidden.size(), "resumed sidebar must restore hidden state");
            previous.replayHiddenPlayerList(viewer, spectator);
            assertEquals(3, renderer.hidden.size(), "showPlayer must reapply hiding");
            previous.removeHiddenPlayerListTarget(viewer, spectator.getUniqueId());
            assertEquals(2, renderer.shown.size());
            previous.replayHiddenPlayerList(viewer, spectator);
            assertEquals(3, renderer.hidden.size(), "departed target must be forgotten");
        } finally {
            manager.releaseDisplayNameOwnership(current, viewer);
            manager.releaseDisplayNameOwnership(previous, viewer);
        }
    }

    @Test
    void allocatesStableCollisionFreeTeamNames() {
        Sidebar sidebar = sidebar();

        assertEquals("bw_t_0", sidebar.teamName("first-player"));
        assertEquals("bw_t_0", sidebar.teamName("first-player"));
        assertEquals("bw_t_1", sidebar.teamName("second-player"));
        assertNotEquals(sidebar.teamName("FB"), sidebar.teamName("Ea"),
                "Java hash collisions must not merge scoreboard teams");

        Set<String> names = new HashSet<>();
        for (int index = 0; index < 10_000; index++) {
            String name = sidebar.teamName("player-" + index);
            assertTrue(name.length() <= 16);
            assertTrue(names.add(name));
        }
    }

    @Test
    void unchangedRefreshDoesNotWriteScoreboardTeam() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState("Alice");
        Team team = team(state);
        Scoreboard scoreboard = scoreboard(team);
        Player player = player("Alice");
        PlayerTab unchanged = tab(player, new SidebarLine(), ChatColor.WHITE,
                PlayerTab.NameTagVisibility.ALWAYS);

        sidebar.applyTab(scoreboard, unchanged);
        assertEquals(0, state.writeCount);

        PlayerTab changed = tab(player, new SidebarLine("&a队伍"), ChatColor.RED,
                PlayerTab.NameTagVisibility.NEVER);
        sidebar.applyTab(scoreboard, changed);
        assertEquals(3, state.writeCount);

        sidebar.applyTab(scoreboard, changed);
        assertEquals(3, state.writeCount, "repeating the same refresh must not send more team updates");
    }

    @Test
    void reattachesOnlyTheActiveOwnerWhenItsManagedScoreboardWasReplaced() {
        Scoreboard managed = scoreboard(team(new TeamState()));
        Scoreboard external = scoreboard(team(new TeamState()));

        assertTrue(Sidebar.shouldReattachScoreboard(true, managed, external));
        assertFalse(Sidebar.shouldReattachScoreboard(false, managed, external));
        assertFalse(Sidebar.shouldReattachScoreboard(true, managed, managed));
        assertFalse(Sidebar.shouldReattachScoreboard(true, null, external));
    }

    @Test
    void periodicReplayReattachesADriftedViewerAfterMapIteration() {
        AtomicInteger reattachments = new AtomicInteger();
        Sidebar sidebar = new Sidebar(new SidebarLine(), List.of(), new ConcurrentLinkedQueue<>()) {
            @Override
            public void add(Player player) {
                reattachments.incrementAndGet();
            }
        };
        Scoreboard managed = scoreboard(team(new TeamState()));
        Scoreboard external = scoreboard(team(new TeamState()));
        String name = "DriftedViewer";
        java.util.UUID viewerId = java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        Player viewer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> viewerId;
                    case "isOnline" -> true;
                    case "getScoreboard" -> external;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        fieldMap(sidebar, "scoreboards").put(viewerId, managed);
        fieldMap(sidebar, "viewers").put(viewerId, viewer);

        SidebarManager manager = SidebarManager.getInstance();
        manager.claimDisplayNameOwnership(sidebar, viewer);
        try {
            sidebar.playerTabRefreshAnimation();
            assertEquals(1, reattachments.get());
        } finally {
            manager.releaseDisplayNameOwnership(sidebar, viewer);
        }
    }

    @Test
    void rendersTheCompletePlayerListRowWithAnExplicitTeamColoredName() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("Alice");
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        PlayerTab tab = new PlayerTab("alice", target,
                new SidebarLine("&7[&cR&7] "), new SidebarLine("&8 !"),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS);

        sidebar.applyTab(scoreboard, tab);
        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(1, renderer.rendered.size());
        assertSame(viewer, renderer.rendered.getFirst().viewer());
        assertSame(target, renderer.rendered.getFirst().target());
        assertEquals("\u00a77[\u00a7cR\u00a77] \u00a7cAlice\u00a78 !",
                renderer.rendered.getFirst().displayName());
        assertEquals(Sidebar.component("§7[§cR§7] "), state.prefix);
        assertEquals(Sidebar.component("§8 !"), state.suffix);
        assertSame(ChatColor.RED, state.color);
        assertTrue(state.entries.contains("Alice"));
    }

    @Test
    void rendersRespawningPlayerNamesInItalic() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("RespawningAlice");
        PlayerTab tab = new PlayerTab("respawning-alice", target,
                new SidebarLine("§7[§cR§7] "), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS);
        tab.setItalic(true);

        sidebar.renderPlayerListName(viewer, tab);

        assertEquals("§7[§cR§7] §c§oRespawningAlice", renderer.rendered.getFirst().displayName());
    }

    @Test
    void spectatorRowsKeepTheirScoreboardColorAndOnlyAdvertiseClientSpectatorMode() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("EliminatedAlice");
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        PlayerTab tab = new PlayerTab("eliminated-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.applyTab(scoreboard, tab);
        sidebar.renderPlayerListName(viewer, tab);

        assertSame(ChatColor.RED, state.color,
                "spectator styling must not replace the former team's name color");
        assertTrue(state.entries.contains(target.getName()));
        assertEquals(1, renderer.rendered.size());
        assertEquals("\u00a7cEliminatedAlice", renderer.rendered.getFirst().displayName());
        assertEquals(1, renderer.spectatorModes.size());
        assertSame(viewer, renderer.spectatorModes.getFirst().viewer());
        assertSame(target, renderer.spectatorModes.getFirst().target());
        assertTrue(renderer.restoredGameModes.isEmpty());
    }

    @Test
    void spectatorRowNeverFakesTheViewersOwnGameMode() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("SelfSpectator");
        PlayerTab tab = new PlayerTab("self-spectator", viewer,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.GRAY, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(1, renderer.renderCalls,
                "the self row still needs its explicit team-colored display name");
        assertEquals(0, renderer.spectatorModeCalls,
                "a cosmetic TAB row must never change the viewer's own PlayerInfo game mode");
        assertEquals(0, renderer.restoreGameModeCalls);
    }

    @Test
    void spectatorSelfRowRestoresAnyPreviouslyCachedFakeMode() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("CachedSelfSpectator");
        PlayerTab tab = new PlayerTab("cached-self-spectator", viewer,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.GRAY, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);
        fieldMap(sidebar, "spectatorPlayerListModes").put(
                viewer.getUniqueId(), new HashSet<>(Set.of(viewer.getUniqueId())));

        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(0, renderer.spectatorModeCalls);
        assertEquals(1, renderer.restoreGameModeCalls,
                "a self row inherited from the old behavior must be repaired immediately");
        assertSame(viewer, renderer.restoredGameModes.getFirst().target());
    }

    @Test
    void legacyPlayerTabConstructorKeepsTheTargetsActualGameMode() {
        PlayerTab tab = new PlayerTab("legacy", player("Legacy"),
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.WHITE, PlayerTab.NameTagVisibility.ALWAYS);

        assertSame(PlayerTab.PlayerListMode.ACTUAL, tab.getPlayerListMode());
    }

    @Test
    void creatingATabRowNeverMutatesTheTargetsGlobalPlayerListName() {
        Sidebar sidebar = sidebar();
        AtomicBoolean globalNameMutated = new AtomicBoolean();
        String name = "ExternalAlice";
        Player target = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> java.util.UUID.nameUUIDFromBytes(
                            name.getBytes(StandardCharsets.UTF_8));
                    case "playerListName" -> {
                        if (args != null && args.length == 1) globalNameMutated.set(true);
                        yield Component.text("[VIP] " + name);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        sidebar.playerTabCreate("external-alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);

        assertFalse(globalNameMutated.get(),
                "per-viewer BedWars formatting must not overwrite Paper's global player-list name");
    }

    @Test
    void cachesUnchangedRowsAndReRendersChangedFormattingBeforeRestoringServerState() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("Alice");
        String[] prefix = {"&7["};
        SidebarLine changingPrefix = new SidebarLine() {
            @Override
            public String getLine() {
                return prefix[0];
            }
        };
        PlayerTab tab = new PlayerTab("alice", target, changingPrefix, new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.BLUE,
                PlayerTab.NameTagVisibility.ALWAYS);

        sidebar.renderPlayerListName(viewer, tab);
        sidebar.renderPlayerListName(viewer, tab);
        assertEquals(1, renderer.rendered.size(), "an unchanged row must not resend packets");

        prefix[0] = "&8[";
        sidebar.renderPlayerListName(viewer, tab);
        assertEquals(2, renderer.rendered.size(),
                "a changed prefix must update the explicit player-list display name");
        assertEquals("\u00a78[\u00a79Alice", renderer.rendered.getLast().displayName());

        sidebar.restorePlayerListName(viewer, tab);
        sidebar.restorePlayerListName(viewer, tab);
        assertEquals(1, renderer.restored.size(), "a managed row is restored exactly once");
        assertSame(target, renderer.restored.getFirst().target());
    }

    @Test
    void offlineRowsReleaseTheirPerViewerCacheWithoutSendingARestorePacket() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        boolean[] online = {true};
        Player target = player("OfflineAlice", () -> online[0]);
        PlayerTab tab = new PlayerTab("offline-alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS, PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);
        online[0] = false;
        sidebar.restorePlayerListName(viewer, tab);
        online[0] = true;
        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(2, renderer.rendered.size(),
                "removing an offline target must release its cached row");
        assertEquals(2, renderer.spectatorModes.size(),
                "offline cleanup must release the cached spectator mode too");
        assertTrue(renderer.restored.isEmpty(),
                "an offline target no longer has a PlayerInfo row to restore");
    }

    @Test
    void forcedReplayRepairsAPlayerInfoEntryOverwrittenAfterRendering() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("Alice");
        PlayerTab tab = sidebar.playerTabCreate("alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);

        sidebar.renderPlayerListName(viewer, tab);
        sidebar.forcePlayerListNameRefresh(viewer);

        assertEquals(2, renderer.rendered.size(),
                "a later ADD_PLAYER or third-party update must be repairable even when text did not change");
    }

    @Test
    void forcedReplayRepairsBothTheExplicitNameAndSpectatorMode() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("SpectatorAlice");
        PlayerTab tab = sidebar.playerTabCreate("spectator-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);
        sidebar.forcePlayerListNameRefresh(viewer);

        assertEquals(2, renderer.renderCalls);
        assertEquals(2, renderer.spectatorModeCalls,
                "a PlayerInfo replay must also repair overwritten spectator styling");
    }

    @Test
    void failedGameModeWritesRetryWithoutPoisoningEitherModeCache() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("SpectatorAlice");
        PlayerTab tab = new PlayerTab("spectator-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        renderer.spectatorModeResult = false;
        sidebar.renderPlayerListName(viewer, tab);
        renderer.spectatorModeResult = true;
        sidebar.renderPlayerListName(viewer, tab);
        sidebar.renderPlayerListName(viewer, tab);
        assertEquals(2, renderer.spectatorModeCalls,
                "a failed spectator write must retry once and cache only the success");

        tab.setPlayerListMode(PlayerTab.PlayerListMode.ACTUAL);
        renderer.restoreGameModeResult = false;
        sidebar.renderPlayerListName(viewer, tab);
        renderer.restoreGameModeResult = true;
        sidebar.renderPlayerListName(viewer, tab);
        sidebar.renderPlayerListName(viewer, tab);
        assertEquals(2, renderer.restoreGameModeCalls,
                "a failed actual-mode restore must retry once and cache only the success");
    }

    @Test
    void spectatorModeWinsAcrossDuplicateRowsUntilTheLastSpectatorRowIsRemoved() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("DuplicateAlice");
        PlayerTab spectatorTab = sidebar.playerTabCreate("duplicate-spectator", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);
        PlayerTab actualTab = sidebar.playerTabCreate("duplicate-actual", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.ACTUAL);

        sidebar.renderPlayerListName(viewer, spectatorTab);
        sidebar.renderPlayerListName(viewer, actualTab);
        assertEquals(1, renderer.spectatorModeCalls);
        assertEquals(0, renderer.restoreGameModeCalls,
                "one actual duplicate must not override another managed spectator row");

        sidebar.removeTab("duplicate-spectator");
        sidebar.renderPlayerListName(viewer, actualTab);
        assertEquals(1, renderer.restoreGameModeCalls,
                "the real game mode is restored after the last spectator row disappears");
    }

    @Test
    void combinedRestoreRunsWhenOnlySpectatorModeWasSuccessfullyManaged() {
        RecordingRenderer renderer = new RecordingRenderer();
        renderer.renderResult = false;
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("SpectatorAlice");
        PlayerTab tab = new PlayerTab("spectator-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);
        sidebar.restorePlayerListName(viewer, tab);

        assertEquals(1, renderer.renderCalls);
        assertEquals(1, renderer.spectatorModeCalls);
        assertEquals(1, renderer.restoreCalls,
                "cleanup must restore mode even if the explicit-name write never succeeded");
        assertSame(target, renderer.restored.getFirst().target());
    }

    @Test
    void successfulNameWriteCannotDiscardAPendingModeRestore() {
        RecordingRenderer renderer = new RecordingRenderer();
        renderer.renderResult = false;
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("SpectatorAlice");
        PlayerTab tab = new PlayerTab("spectator-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);
        renderer.restoreResult = false;
        sidebar.restorePlayerListName(viewer, tab);

        renderer.renderResult = true;
        renderer.restoreGameModeResult = false;
        tab.setPlayerListMode(PlayerTab.PlayerListMode.ACTUAL);
        sidebar.renderPlayerListName(viewer, tab);

        Map<java.util.UUID, Map<java.util.UUID, Player>> pending =
                fieldMap(sidebar, "pendingPlayerListRestores");
        assertTrue(pending.get(viewer.getUniqueId()).containsKey(target.getUniqueId()),
                "a successful explicit-name write must not hide a failed combined mode restore");

        renderer.restoreResult = true;
        sidebar.forcePlayerListNameRefresh(viewer);
        assertEquals(2, renderer.restoreCalls,
                "the next explicit refresh must retry the complete restore");
        assertTrue(fieldMap(sidebar, "pendingPlayerListRestores").isEmpty());
    }

    @Test
    void completeNewTakeoverSupersedesAStalePendingRestore() {
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("SpectatorAlice");
        PlayerTab tab = new PlayerTab("spectator-alice", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                List.of(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);

        sidebar.renderPlayerListName(viewer, tab);
        renderer.restoreResult = false;
        sidebar.restorePlayerListName(viewer, tab);
        assertEquals(1, renderer.restoreCalls);

        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(2, renderer.renderCalls,
                "a stale pending cleanup requires a fresh explicit-name takeover");
        assertEquals(2, renderer.spectatorModeCalls,
                "a stale pending cleanup requires a fresh spectator-mode takeover");
        assertTrue(fieldMap(sidebar, "pendingPlayerListRestores").isEmpty(),
                "both successful writes supersede the stale cleanup request");

        renderer.restoreResult = true;
        sidebar.forcePlayerListNameRefresh(viewer);
        assertEquals(1, renderer.restoreCalls,
                "a later refresh must not restore state superseded by the new row");
    }

    @Test
    void failedPacketWriteDoesNotPoisonTheRenderedNameCache() {
        RecordingRenderer renderer = new RecordingRenderer();
        renderer.renderResult = false;
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("Viewer");
        Player target = player("Alice");
        PlayerTab tab = new PlayerTab("alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS);

        sidebar.renderPlayerListName(viewer, tab);
        renderer.renderResult = true;
        sidebar.renderPlayerListName(viewer, tab);

        assertEquals(2, renderer.renderCalls,
                "an unchanged row must retry after the previous packet write failed");
    }

    @Test
    void staleSidebarCannotReleaseALaterOwnersDisplayNames() {
        SidebarManager manager = SidebarManager.getInstance();
        Sidebar oldSidebar = sidebar(new RecordingRenderer());
        Sidebar currentSidebar = sidebar(new RecordingRenderer());
        Player viewer = player("Viewer");

        manager.claimDisplayNameOwnership(oldSidebar, viewer);
        manager.claimDisplayNameOwnership(currentSidebar, viewer);

        assertFalse(manager.releaseDisplayNameOwnership(oldSidebar, viewer));
        assertTrue(manager.ownsDisplayNames(currentSidebar, viewer));
        assertTrue(manager.releaseDisplayNameOwnership(currentSidebar, viewer));
    }

    @Test
    void suspendedOwnerCannotRetryAPendingRestoreOverTheCurrentOwner() {
        SidebarManager manager = SidebarManager.getInstance();
        RecordingRenderer previousRenderer = new RecordingRenderer();
        Sidebar previous = sidebar(previousRenderer);
        Sidebar current = sidebar(new RecordingRenderer());
        Player viewer = player("OwnershipViewer");
        Player target = player("PreviousSpectator");
        PlayerTab previousTab = previous.playerTabCreate("previous-spectator", target,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);
        fieldMap(previous, "scoreboards").put(viewer.getUniqueId(), scoreboard(team(new TeamState())));
        fieldMap(previous, "viewers").put(viewer.getUniqueId(), viewer);

        manager.claimDisplayNameOwnership(previous, viewer);
        try {
            previous.renderPlayerListName(viewer, previousTab);
            previousRenderer.restoreResult = false;
            manager.claimDisplayNameOwnership(current, viewer);
            assertEquals(1, previousRenderer.restoreCalls);

            previous.playerTabRefreshAnimation();

            assertEquals(1, previousRenderer.restoreCalls,
                    "a suspended layer must not overwrite the current owner's PlayerInfo rows");
        } finally {
            manager.releaseDisplayNameOwnership(previous, viewer);
            manager.releaseDisplayNameOwnership(current, viewer);
        }
    }

    @Test
    void removingTabsReleasesTheirAllocatedTeamNames() {
        Sidebar neverRendered = sidebar();
        Player unrendered = player("MappingUnrendered");
        neverRendered.playerTabCreate("unrendered", unrendered, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.GRAY);
        neverRendered.removeTab("unrendered");
        assertTrue(fieldMap(neverRendered, "tabTeamNames").isEmpty(),
                "removing an unrendered row must not allocate a team name on the cleanup path");

        Sidebar singleRemoval = sidebar();
        Player alice = player("MappingAlice");
        singleRemoval.playerTabCreate("alice", alice, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);
        singleRemoval.teamName("alice");

        singleRemoval.removeTab("alice");

        assertFalse(fieldMap(singleRemoval, "tabTeamNames").containsKey("alice"),
                "removeTab must release identifiers belonging to players that have left");

        Sidebar bulkRemoval = sidebar();
        Player bob = player("MappingBob");
        Player charlie = player("MappingCharlie");
        bulkRemoval.playerTabCreate("bob", bob, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.BLUE);
        bulkRemoval.playerTabCreate("charlie", charlie, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.GREEN);
        bulkRemoval.teamName("bob");
        bulkRemoval.teamName("charlie");

        bulkRemoval.removeTabs();

        assertTrue(fieldMap(bulkRemoval, "tabTeamNames").isEmpty(),
                "removeTabs must not retain identifiers from historical players");
    }

    @Test
    void releasingANewerSidebarRestoresAndReplaysThePreviousOwner() {
        SidebarManager manager = SidebarManager.getInstance();
        RecordingRenderer previousRenderer = new RecordingRenderer();
        RecordingRenderer currentRenderer = new RecordingRenderer();
        Sidebar previous = sidebar(previousRenderer);
        Sidebar current = sidebar(currentRenderer);
        Player viewer = player("OwnershipViewer");
        Player previousTarget = player("PreviousTarget");
        Player currentTarget = player("CurrentTarget");
        PlayerTab previousTab = previous.playerTabCreate("previous", previousTarget,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.RED, PlayerTab.NameTagVisibility.ALWAYS,
                PlayerTab.PlayerListMode.SPECTATOR);
        PlayerTab currentTab = current.playerTabCreate("current", currentTarget,
                new SidebarLine(), new SidebarLine(), PlayerTab.PushingRule.NEVER,
                new ConcurrentLinkedQueue<>(), ChatColor.BLUE);

        manager.claimDisplayNameOwnership(previous, viewer);
        try {
            previous.renderPlayerListName(viewer, previousTab);
            manager.claimDisplayNameOwnership(current, viewer);

            assertEquals(1, previousRenderer.restoreCalls,
                    "claiming a newer Sidebar must remove the previous owner's client rows");
            assertSame(previousTarget, previousRenderer.restored.getFirst().target());

            current.renderPlayerListName(viewer, currentTab);
            assertTrue(manager.releaseDisplayNameOwnership(current, viewer));

            assertTrue(manager.ownsDisplayNames(previous, viewer),
                    "releasing the newer Sidebar must reactivate the still-attached previous owner");
            assertEquals(2, previousRenderer.rendered.size(),
                    "reactivating the previous owner must replay its complete player list state");
            assertEquals(2, previousRenderer.spectatorModeCalls,
                    "reactivating the previous owner must replay spectator styling too");
        } finally {
            manager.releaseDisplayNameOwnership(current, viewer);
            manager.releaseDisplayNameOwnership(previous, viewer);
        }
    }

    @Test
    void removingASuspendedSidebarRepairsTheNewerScoreboardFallback() {
        SidebarManager manager = SidebarManager.getInstance();
        Sidebar previous = sidebar(new RecordingRenderer());
        Sidebar current = sidebar(new RecordingRenderer());
        Player viewer = player("NonLifoViewer");
        Scoreboard external = scoreboard(team(new TeamState()));
        Scoreboard previousManaged = scoreboard(team(new TeamState()));
        fieldMap(current, "previousScoreboards").put(viewer.getUniqueId(), previousManaged);

        manager.claimDisplayNameOwnership(previous, viewer);
        manager.claimDisplayNameOwnership(current, viewer);
        try {
            manager.unlinkPreviousScoreboard(previous, viewer, previousManaged, external);
            assertFalse(manager.releaseDisplayNameOwnership(previous, viewer));

            assertSame(external,
                    fieldMap(current, "previousScoreboards").get(viewer.getUniqueId()),
                    "removing a suspended owner must not leave its dead scoreboard as the fallback");
        } finally {
            manager.releaseDisplayNameOwnership(current, viewer);
            manager.releaseDisplayNameOwnership(previous, viewer);
        }
    }

    @Test
    void replacingAnIdentifierRemovesThePreviousPlayerFromItsScoreboardTeam() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        Player viewer = player("ReplacementViewer");
        Player alice = player("ReplacementAlice");
        Player bob = player("ReplacementBob");
        fieldMap(sidebar, "scoreboards").put(viewer.getUniqueId(), scoreboard);
        fieldMap(sidebar, "viewers").put(viewer.getUniqueId(), viewer);

        sidebar.playerTabCreate("slot", alice, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);
        sidebar.playerTabCreate("slot", bob, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.BLUE);

        assertFalse(state.entries.contains(alice.getName()),
                "the old player must not keep the replacement row's overhead color");
        assertTrue(state.entries.contains(bob.getName()));
    }

    @Test
    void failedRestoreRemainsPendingUntilAnExplicitRefreshSucceeds() {
        SidebarManager manager = SidebarManager.getInstance();
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("RetryViewer");
        Player target = player("RetryTarget");
        PlayerTab tab = sidebar.playerTabCreate("retry", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);
        fieldMap(sidebar, "viewers").put(viewer.getUniqueId(), viewer);

        manager.claimDisplayNameOwnership(sidebar, viewer);
        try {
            sidebar.renderPlayerListName(viewer, tab);
            renderer.restoreResult = false;

            sidebar.removeTab("retry");

            assertEquals(1, renderer.restoreCalls);
            renderer.restoreResult = true;
            sidebar.forcePlayerListNameRefresh(viewer);
            assertEquals(2, renderer.restoreCalls,
                    "an explicit refresh must retry a restore that previously failed");

            sidebar.forcePlayerListNameRefresh(viewer);
            assertEquals(2, renderer.restoreCalls,
                    "a successful retry must clear the pending restore state");
        } finally {
            manager.releaseDisplayNameOwnership(sidebar, viewer);
        }
    }

    @Test
    void failedBulkRestoreKeepsAllTargetsUntilRefreshSucceeds() {
        SidebarManager manager = SidebarManager.getInstance();
        RecordingRenderer renderer = new RecordingRenderer();
        Sidebar sidebar = sidebar(renderer);
        Player viewer = player("BulkRetryViewer");
        Player alice = player("BulkRetryAlice");
        Player bob = player("BulkRetryBob");
        PlayerTab aliceTab = sidebar.playerTabCreate("bulk-alice", alice, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.RED);
        PlayerTab bobTab = sidebar.playerTabCreate("bulk-bob", bob, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, new ConcurrentLinkedQueue<>(), ChatColor.BLUE);
        fieldMap(sidebar, "viewers").put(viewer.getUniqueId(), viewer);

        manager.claimDisplayNameOwnership(sidebar, viewer);
        try {
            sidebar.renderPlayerListName(viewer, aliceTab);
            sidebar.renderPlayerListName(viewer, bobTab);
            renderer.restoreResult = false;

            sidebar.removeTabs();
            assertEquals(1, renderer.restoreCalls);

            renderer.restoreResult = true;
            sidebar.forcePlayerListNameRefresh(viewer);
            assertEquals(2, renderer.restoreCalls);
            assertEquals(Set.of(alice.getUniqueId(), bob.getUniqueId()),
                    renderer.restored.subList(2, 4).stream()
                            .map(captured -> captured.target().getUniqueId())
                            .collect(java.util.stream.Collectors.toSet()));
        } finally {
            manager.releaseDisplayNameOwnership(sidebar, viewer);
        }
    }

    @Test
    void capturesOnlyTheLatestExternallyOwnedScoreboard() {
        Scoreboard managed = scoreboard(team(new TeamState()));
        Scoreboard external = scoreboard(team(new TeamState()));

        assertFalse(Sidebar.shouldCapturePreviousScoreboard(managed, managed));
        assertTrue(Sidebar.shouldCapturePreviousScoreboard(managed, external));
        assertTrue(Sidebar.shouldCapturePreviousScoreboard(null, external));
    }

    @Test
    void appliesRequestedColorAndAddsPlayerToScoreboardTeam() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        Player player = player("Alice");

        sidebar.applyTab(scoreboard, tab(player, new SidebarLine("&cRed "), ChatColor.RED,
                PlayerTab.NameTagVisibility.NEVER));

        assertSame(ChatColor.RED, state.color);
        assertTrue(state.entries.contains("Alice"));
        assertSame(Team.OptionStatus.NEVER, state.visibility);
        assertEquals(Sidebar.component("§cRed "), state.prefix);
    }

    @Test
    void doesNotReadPaperAdventureColorForNamedScoreboardColors() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        state.color = ChatColor.RESET;
        Scoreboard scoreboard = scoreboard(teamThatRejectsAdventureColorReads(state));
        Player player = player("Alice");

        sidebar.applyTab(scoreboard, tab(player, new SidebarLine(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS));

        assertSame(NamedTextColor.RED, state.modernColor);
        assertSame(ChatColor.RED, state.color);
    }

    @Test
    void refreshesColorForAnExistingSharedCollisionTeam() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(teamThatRejectsAdventureColorReads(state));
        Player target = player("Alice");
        PlayerTab tab = new PlayerTab(
                "alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.PUSH_OTHER_TEAMS, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS, PlayerTab.PlayerListMode.ACTUAL,
                "red-team");

        sidebar.applyTab(scoreboard, tab);

        assertSame(NamedTextColor.RED, state.modernColor,
                "a shared collision team must still receive later colour updates");
    }

    @Test
    void sharedCollisionTeamsBlockOnlyTeammatePushing() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        Player target = player("Alice");
        PlayerTab tab = new PlayerTab(
                "alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.PUSH_OTHER_TEAMS, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS, PlayerTab.PlayerListMode.ACTUAL,
                "red-team");

        sidebar.applyTab(scoreboard, tab);

        assertSame(Team.OptionStatus.FOR_OTHER_TEAMS, state.collision,
                "members of one shared team must not push each other");
        assertSame(Team.OptionStatus.ALWAYS, state.visibility,
                "collision policy must not alter name-tag visibility");
    }

    @Test
    void invisiblePlayingRowsStillUseTheirTeamCollisionGroup() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        Player target = player("InvisibleAlice");
        PlayerTab tab = new PlayerTab(
                "invisible-alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.PUSH_OTHER_TEAMS, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.NEVER, PlayerTab.PlayerListMode.ACTUAL,
                "red-team");

        sidebar.applyTab(scoreboard, tab);

        assertSame(Team.OptionStatus.FOR_OTHER_TEAMS, state.collision,
                "an invisible live player must still share teammate collision rules");
        assertSame(Team.OptionStatus.ALWAYS, state.visibility,
                "the shared collision team must remain usable by all teammates");
    }

    @Test
    void activePregameRowsRestoreCollisionAfterAnInvisiblePlayingRow() {
        Sidebar sidebar = sidebar();
        TeamState state = new TeamState();
        Scoreboard scoreboard = scoreboard(team(state));
        Player target = player("Alice");
        PlayerTab tab = new PlayerTab(
                "alice", target, new SidebarLine(), new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), ChatColor.RED,
                PlayerTab.NameTagVisibility.ALWAYS, PlayerTab.PlayerListMode.ACTUAL,
                null);
        state.collision = Team.OptionStatus.NEVER;

        sidebar.applyTab(scoreboard, tab);

        assertSame(Team.OptionStatus.ALWAYS, state.collision,
                "waiting and starting players must not retain the playing invisibility rule");
    }

    private static Sidebar sidebar() {
        return new Sidebar(new SidebarLine(), List.of(), new ConcurrentLinkedQueue<>());
    }

    private static Sidebar sidebar(PlayerListDisplayNameRenderer renderer) {
        return new Sidebar(new SidebarLine(), List.of(), new ConcurrentLinkedQueue<>(), renderer);
    }

    private static PlayerTab tab(Player player, SidebarLine prefix, ChatColor color,
                                 PlayerTab.NameTagVisibility visibility) {
        return new PlayerTab(player.getUniqueId().toString(), player, prefix, new SidebarLine(),
                PlayerTab.PushingRule.NEVER, List.of(), color, visibility);
    }

    private static Player player(String name) {
        return player(name, () -> true);
    }

    private static Player player(String name, BooleanSupplier online) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
                    case "isOnline" -> online.getAsBoolean();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Scoreboard scoreboard(Team team) {
        return (Scoreboard) Proxy.newProxyInstance(Scoreboard.class.getClassLoader(),
                new Class<?>[]{Scoreboard.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTeam" -> team;
                    case "registerNewTeam" -> throw new AssertionError("the existing team must be reused");
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Team team(TeamState state) {
        return (Team) Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prefix" -> {
                        if (args == null || args.length == 0) yield state.prefix;
                        state.prefix = (Component) args[0];
                        state.writeCount++;
                        yield null;
                    }
                    case "suffix" -> {
                        if (args == null || args.length == 0) yield state.suffix;
                        state.suffix = (Component) args[0];
                        state.writeCount++;
                        yield null;
                    }
                    case "getColor" -> state.color;
                    case "setColor" -> {
                        state.color = (ChatColor) args[0];
                        state.writeCount++;
                        yield null;
                    }
                    case "getOption" -> args[0] == Team.Option.COLLISION_RULE
                            ? state.collision : state.visibility;
                    case "setOption" -> {
                        if (args[0] == Team.Option.COLLISION_RULE) {
                            state.collision = (Team.OptionStatus) args[1];
                        } else {
                            state.visibility = (Team.OptionStatus) args[1];
                        }
                        state.writeCount++;
                        yield null;
                    }
                    case "hasEntry" -> state.entries.contains((String) args[0]);
                    case "addEntry" -> {
                        state.entries.add((String) args[0]);
                        state.writeCount++;
                        yield null;
                    }
                    case "removeEntry" -> {
                        boolean removed = state.entries.remove((String) args[0]);
                        state.writeCount++;
                        yield removed;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Team teamThatRejectsAdventureColorReads(TeamState state) {
        return (Team) Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prefix" -> {
                        if (args == null || args.length == 0) yield state.prefix;
                        state.prefix = (Component) args[0];
                        yield null;
                    }
                    case "suffix" -> {
                        if (args == null || args.length == 0) yield state.suffix;
                        state.suffix = (Component) args[0];
                        yield null;
                    }
                    case "color" -> {
                        if (args == null || args.length == 0) {
                            throw new IllegalStateException("Team colors must have hex values");
                        }
                        state.modernColor = (NamedTextColor) args[0];
                        if (state.modernColor == NamedTextColor.RED) state.color = ChatColor.RED;
                        yield null;
                    }
                    case "getColor" -> state.color;
                    case "setColor" -> {
                        state.color = (ChatColor) args[0];
                        yield null;
                    }
                    case "getOption" -> args[0] == Team.Option.COLLISION_RULE
                            ? state.collision : state.visibility;
                    case "setOption" -> {
                        if (args[0] == Team.Option.COLLISION_RULE) {
                            state.collision = (Team.OptionStatus) args[1];
                        } else {
                            state.visibility = (Team.OptionStatus) args[1];
                        }
                        yield null;
                    }
                    case "hasEntry" -> state.entries.contains((String) args[0]);
                    case "addEntry" -> {
                        state.entries.add((String) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> fieldMap(Sidebar sidebar, String fieldName) {
        try {
            Field field = Sidebar.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Map<K, V>) field.get(sidebar);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Sidebar." + fieldName, exception);
        }
    }

    private static final class TeamState {
        private Component prefix = Component.empty();
        private Component suffix = Component.empty();
        private ChatColor color = ChatColor.WHITE;
        private NamedTextColor modernColor = NamedTextColor.WHITE;
        private Team.OptionStatus visibility = Team.OptionStatus.ALWAYS;
        private Team.OptionStatus collision = Team.OptionStatus.ALWAYS;
        private final Set<String> entries = new HashSet<>();
        private int writeCount;

        private TeamState() {
        }

        private TeamState(String entry) {
            entries.add(entry);
        }
    }

    private static final class RecordingRenderer implements PlayerListDisplayNameRenderer {
        private final java.util.ArrayList<CapturedName> rendered = new java.util.ArrayList<>();
        private final java.util.ArrayList<CapturedTarget> spectatorModes = new java.util.ArrayList<>();
        private final java.util.ArrayList<CapturedTarget> restoredGameModes = new java.util.ArrayList<>();
        private final java.util.ArrayList<CapturedTarget> restored = new java.util.ArrayList<>();
        private final java.util.ArrayList<CapturedTarget> hidden = new java.util.ArrayList<>();
        private final java.util.ArrayList<CapturedTarget> shown = new java.util.ArrayList<>();
        private boolean renderResult = true;
        private boolean spectatorModeResult = true;
        private boolean restoreGameModeResult = true;
        private boolean restoreResult = true;
        private int renderCalls;
        private int spectatorModeCalls;
        private int restoreGameModeCalls;
        private int restoreCalls;

        @Override
        public boolean render(Player viewer,
                              java.util.Collection<PlayerListDisplayNameRenderer.RenderedName> names) {
            renderCalls++;
            names.forEach(name -> rendered.add(
                    new CapturedName(viewer, name.target(), name.legacyDisplayName())));
            return renderResult;
        }

        @Override
        public boolean setSpectatorMode(Player viewer, java.util.Collection<Player> targets) {
            spectatorModeCalls++;
            targets.forEach(target -> spectatorModes.add(new CapturedTarget(viewer, target)));
            return spectatorModeResult;
        }

        @Override
        public boolean restoreGameMode(Player viewer, java.util.Collection<Player> targets) {
            restoreGameModeCalls++;
            targets.forEach(target -> restoredGameModes.add(new CapturedTarget(viewer, target)));
            return restoreGameModeResult;
        }

        @Override
        public boolean restore(Player viewer, java.util.Collection<Player> targets) {
            restoreCalls++;
            targets.forEach(target -> restored.add(new CapturedTarget(viewer, target)));
            return restoreResult;
        }

        @Override
        public boolean hidePlayerList(Player viewer, java.util.Collection<Player> targets) {
            targets.forEach(target -> hidden.add(new CapturedTarget(viewer, target)));
            return true;
        }

        @Override
        public boolean showPlayerList(Player viewer, java.util.Collection<Player> targets) {
            targets.forEach(target -> shown.add(new CapturedTarget(viewer, target)));
            return true;
        }
    }

    private record CapturedTarget(Player viewer, Player target) {
    }

    private record CapturedName(Player viewer, Player target, String displayName) {
    }
}
