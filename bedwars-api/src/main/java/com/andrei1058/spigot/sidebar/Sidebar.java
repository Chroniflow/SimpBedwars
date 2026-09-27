package com.andrei1058.spigot.sidebar;

import com.andrei1058.bedwars.api.util.AdventureText;
import io.papermc.paper.scoreboard.numbers.FixedFormat;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Sidebar {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String SIDEBAR_OBJECTIVE = "bw_sidebar";
    private static final String HEALTH_BELOW_OBJECTIVE = "bw_health";
    private static final String HEALTH_TAB_OBJECTIVE = "bw_health_tab";
    private static final String LINE_TEAM_PREFIX = "bw_l_";
    private static final String TAB_TEAM_PREFIX = "bw_t_";
    private static final String COLLISION_TEAM_PREFIX = "bw_c_";
    private static final String[] LINE_ENTRIES = {
            "\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74",
            "\u00a75", "\u00a76", "\u00a77", "\u00a78", "\u00a79",
            "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d", "\u00a7e"
    };

    private SidebarLine title;
    private final List<SidebarLine> lines = new ArrayList<>();
    private final ConcurrentLinkedQueue<PlaceholderProvider> placeholders = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, Player> viewers = new HashMap<>();
    private final Map<UUID, Map<UUID, String>> renderedPlayerListNames = new HashMap<>();
    private final Map<UUID, Set<UUID>> spectatorPlayerListModes = new HashMap<>();
    private final Map<UUID, Map<UUID, Player>> pendingPlayerListRestores = new HashMap<>();
    private final Map<UUID, Map<UUID, Player>> desiredHiddenPlayerLists = new HashMap<>();
    private final Map<UUID, Map<UUID, Player>> appliedHiddenPlayerLists = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<String, PlayerTab> tabs = new HashMap<>();
    private final Map<String, String> tabTeamNames = new HashMap<>();
    private final Map<String, String> collisionTeamNames = new HashMap<>();
    private int nextTabTeamId;
    private int nextCollisionTeamId;
    private SidebarLine healthLine = new SidebarLine();
    private boolean healthEnabled = false;
    private boolean healthInTab = false;
    private final PlayerListDisplayNameRenderer displayNameRenderer;

    public Sidebar(@NotNull SidebarLine title, @NotNull Collection<SidebarLine> lines,
                   @NotNull Collection<PlaceholderProvider> placeholders) {
        this(title, lines, placeholders, PlayerListDisplayNamePackets.instance());
    }

    Sidebar(@NotNull SidebarLine title, @NotNull Collection<SidebarLine> lines,
            @NotNull Collection<PlaceholderProvider> placeholders,
            @NotNull PlayerListDisplayNameRenderer displayNameRenderer) {
        this.title = title;
        this.lines.addAll(lines);
        this.placeholders.addAll(placeholders);
        this.displayNameRenderer = displayNameRenderer;
    }

    public void add(@NotNull Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Scoreboard currentScoreboard = player.getScoreboard();
        Scoreboard managedScoreboard = scoreboards.get(playerId);
        SidebarManager sidebarManager = SidebarManager.getInstance();
        if (managedScoreboard != null
                && sidebarManager.hasDisplayNameOwnership(this, player)
                && !sidebarManager.ownsDisplayNames(this, player)) {
            // Re-attaching a suspended layer must first remove its old place
            // from the ownership chain, otherwise previous scoreboards form a
            // cycle (A -> B -> A).
            Scoreboard oldPrevious = previousScoreboards.remove(playerId);
            sidebarManager.unlinkPreviousScoreboard(this, player, managedScoreboard, oldPrevious);
            sidebarManager.releaseDisplayNameOwnership(this, player);
            scoreboards.remove(playerId);
            viewers.remove(playerId);
            managedScoreboard = null;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        List<RenderedPlayerTab> renderedTabs = renderPlayerTabs(tabs.values());
        // Build off-screen first. Binding a different scoreboard instance makes
        // CraftBukkit send one complete snapshot, including team CREATE data.
        render(scoreboard);
        renderedTabs.forEach(tab -> applyTab(scoreboard, tab));
        renderHealth(scoreboard);
        player.setScoreboard(scoreboard);
        if (shouldCapturePreviousScoreboard(managedScoreboard, currentScoreboard)) {
            // Another plugin may have replaced our board since the previous
            // replay. Restore that latest external board when BedWars releases
            // ownership, not the stale board captured on first entry.
            previousScoreboards.put(playerId, currentScoreboard);
        }
        scoreboards.put(playerId, scoreboard);
        viewers.put(playerId, player);
        renderedPlayerListNames.computeIfAbsent(playerId, ignored -> new HashMap<>());
        spectatorPlayerListModes.computeIfAbsent(playerId, ignored -> new HashSet<>());
        sidebarManager.claimDisplayNameOwnership(this, player);
        retryPendingPlayerListRestores(player);
        renderPlayerListNames(player, renderedTabs, true);
        applyHiddenPlayerListState(player);
    }

    public void remove(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard scoreboard = scoreboards.remove(playerId);
        Player viewer = viewers.remove(playerId);
        Scoreboard previous = previousScoreboards.remove(playerId);
        SidebarManager sidebarManager = SidebarManager.getInstance();
        if (scoreboard == null) {
            sidebarManager.releaseDisplayNameOwnership(this, player);
            restoreHiddenPlayerList(player);
            discardViewerDisplayNameState(playerId);
            return;
        }
        Player ownershipViewer = viewer == null ? player : viewer;
        boolean ownedDisplayNames = viewer != null
                && sidebarManager.ownsDisplayNames(this, viewer);
        if (ownedDisplayNames && viewer.isOnline()) {
            retryPendingPlayerListRestores(viewer);
            restorePlayerListNames(viewer, tabs.values());
        }
        if (viewer != null) restoreHiddenPlayerList(viewer);
        if (player.getScoreboard() == scoreboard) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            player.setScoreboard(previous == null && manager != null ? manager.getMainScoreboard() : previous);
        }
        sidebarManager.unlinkPreviousScoreboard(this, ownershipViewer, scoreboard, previous);
        sidebarManager.releaseDisplayNameOwnership(this, ownershipViewer);
        if (viewer != null) {
            if (!viewer.isOnline() || !pendingPlayerListRestores.containsKey(playerId)) {
                discardViewerDisplayNameState(playerId);
            }
        } else {
            discardViewerDisplayNameState(playerId);
        }
    }

    void replacePreviousScoreboard(@NotNull UUID viewerId, @NotNull Scoreboard expected,
                                   @Nullable Scoreboard replacement) {
        if (previousScoreboards.get(viewerId) != expected) return;
        if (replacement == null) {
            previousScoreboards.remove(viewerId);
        } else {
            previousScoreboards.put(viewerId, replacement);
        }
    }

    public void clearLines() {
        lines.clear();
        renderAll();
    }

    @NotNull
    public Collection<PlaceholderProvider> getPlaceholders() {
        return placeholders;
    }

    public void removePlaceholder(@NotNull String placeholder) {
        placeholders.removeIf(provider -> provider.getPlaceholder().equals(placeholder));
        renderAll();
    }

    public void addPlaceholder(@NotNull PlaceholderProvider placeholderProvider) {
        placeholders.add(placeholderProvider);
        renderAll();
    }

    public void setTitle(@NotNull SidebarLine title) {
        this.title = title;
        refreshTitle();
    }

    public void setContent(@NotNull SidebarLine title, @NotNull Collection<SidebarLine> lines,
                           @NotNull Collection<PlaceholderProvider> placeholders) {
        this.title = title;
        this.lines.clear();
        this.lines.addAll(lines);
        this.placeholders.clear();
        this.placeholders.addAll(placeholders);
        renderAll();
    }

    @NotNull
    public SidebarLine getTitle() {
        return title;
    }

    public void addLine(@NotNull SidebarLine line) {
        lines.add(line);
        renderAll();
    }

    @NotNull
    public List<SidebarLine> getLines() {
        return lines;
    }

    public void refreshTitle() {
        scoreboards.values().forEach(scoreboard -> {
            Objective objective = scoreboard.getObjective(SIDEBAR_OBJECTIVE);
            if (objective != null) {
                objective.displayName(component(renderText(title, placeholders)));
            }
        });
    }

    public void refreshPlaceholders() {
        if (scoreboards.isEmpty()) return;
        RenderedSidebar rendered = renderSidebar();
        scoreboards.values().forEach(scoreboard -> refreshRenderedContent(scoreboard, rendered));
    }

    public void playerTabRefreshAnimation() {
        List<RenderedPlayerTab> renderedTabs = renderPlayerTabs(tabs.values());
        List<Player> detachedViewers = new ArrayList<>();
        scoreboards.forEach((viewerId, scoreboard) -> {
            Player viewer = viewers.get(viewerId);
            if (viewer == null || !viewer.isOnline()) return;
            boolean ownsDisplayNames = SidebarManager.getInstance().ownsDisplayNames(this, viewer);
            if (ownsDisplayNames
                    && shouldReattachScoreboard(true, scoreboard, viewer.getScoreboard())) {
                detachedViewers.add(viewer);
                return;
            }
            renderedTabs.forEach(tab -> applyTab(scoreboard, tab));
            // A visibility change or another plugin can resend ADD_PLAYER or
            // UPDATE_DISPLAY_NAME after our event update. One batched replay
            // per viewer keeps production clients authoritative without a
            // viewer-by-target packet explosion.
            if (ownsDisplayNames) {
                retryPendingPlayerListRestores(viewer);
                renderPlayerListNames(viewer, renderedTabs, true);
            }
        });
        detachedViewers.forEach(this::add);
    }

    public void playerHealthRefreshAnimation() {
        scoreboards.values().forEach(this::renderHealthTitle);
    }

    public void setPlayerHealth(@NotNull Player player, int health) {
        scoreboards.values().forEach(scoreboard -> {
            Objective belowName = scoreboard.getObjective(HEALTH_BELOW_OBJECTIVE);
            if (belowName != null) {
                belowName.getScore(player.getName()).setScore(health);
            }
            Objective tab = scoreboard.getObjective(HEALTH_TAB_OBJECTIVE);
            if (tab != null) {
                tab.getScore(player.getName()).setScore(health);
            }
        });
    }

    /** Remove a player's stale health scores without disabling health for everyone else. */
    public void clearPlayerHealth(@NotNull Player player) {
        scoreboards.values().forEach(scoreboard -> {
            Objective belowName = scoreboard.getObjective(HEALTH_BELOW_OBJECTIVE);
            if (belowName != null) belowName.getScore(player.getName()).resetScore();
            Objective tab = scoreboard.getObjective(HEALTH_TAB_OBJECTIVE);
            if (tab != null) tab.getScore(player.getName()).resetScore();
        });
    }

    public void removeTabs() {
        viewers.values().stream()
                .filter(Player::isOnline)
                .filter(viewer -> SidebarManager.getInstance().ownsDisplayNames(this, viewer))
                .forEach(viewer -> restorePlayerListNames(viewer, tabs.values()));
        viewers.values().stream().filter(Player::isOnline).forEach(this::restoreHiddenPlayerList);
        tabs.values().forEach(tab -> {
            detachTab(tab);
        });
        tabs.clear();
        scoreboards.values().forEach(Sidebar::removeTabTeams);
        tabTeamNames.clear();
        collisionTeamNames.clear();
        nextTabTeamId = 0;
        nextCollisionTeamId = 0;
    }

    /**
     * Synchronize the client-side PlayerInfo visibility for one viewer. The
     * desired set is retained across scoreboard reattachment and reapplied
     * when this Sidebar resumes control of the viewer's list.
     */
    public void synchronizeHiddenPlayerList(@NotNull Player viewer,
                                            @NotNull Collection<Player> hiddenTargets) {
        UUID viewerId = viewer.getUniqueId();
        Map<UUID, Player> desired = new LinkedHashMap<>();
        hiddenTargets.stream()
                .filter(Player::isOnline)
                .filter(target -> !target.getUniqueId().equals(viewerId))
                .forEach(target -> desired.putIfAbsent(target.getUniqueId(), target));
        desiredHiddenPlayerLists.put(viewerId, desired);
        SidebarManager manager = SidebarManager.getInstance();
        if (!viewer.isOnline() || (manager.hasDisplayNameOwnership(this, viewer)
                && !manager.ownsDisplayNames(this, viewer))) return;

        Map<UUID, Player> applied = appliedHiddenPlayerLists.computeIfAbsent(
                viewerId, ignored -> new LinkedHashMap<>());
        List<Player> toHide = desired.entrySet().stream()
                .filter(entry -> !applied.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<Player> toShow = applied.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toHide.isEmpty() && displayNameRenderer.hidePlayerList(viewer, toHide)) {
            toHide.forEach(target -> applied.put(target.getUniqueId(), target));
        }
        if (!toShow.isEmpty() && displayNameRenderer.showPlayerList(viewer, toShow)) {
            toShow.forEach(target -> applied.remove(target.getUniqueId()));
        }
    }

    private void applyHiddenPlayerListState(@NotNull Player viewer) {
        Map<UUID, Player> desired = desiredHiddenPlayerLists.get(viewer.getUniqueId());
        if (desired == null || desired.isEmpty()) return;
        Map<UUID, Player> applied = appliedHiddenPlayerLists.computeIfAbsent(
                viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
        applied.clear();
        if (displayNameRenderer.hidePlayerList(viewer, desired.values())) {
            applied.putAll(desired);
        }
    }

    private void restoreHiddenPlayerList(@NotNull Player viewer) {
        desiredHiddenPlayerLists.remove(viewer.getUniqueId());
        releaseHiddenPlayerList(viewer);
    }

    private void releaseHiddenPlayerList(@NotNull Player viewer) {
        Map<UUID, Player> applied = appliedHiddenPlayerLists.remove(viewer.getUniqueId());
        if (applied != null && !applied.isEmpty() && viewer.isOnline()) {
            displayNameRenderer.showPlayerList(viewer, applied.values());
        }
    }

    /** Reapply one hidden row after Paper has rebuilt the target's PlayerInfo. */
    public void replayHiddenPlayerList(@NotNull Player viewer, @NotNull Player target) {
        Map<UUID, Player> desired = desiredHiddenPlayerLists.get(viewer.getUniqueId());
        if (desired == null || !desired.containsKey(target.getUniqueId())
                || !SidebarManager.getInstance().ownsDisplayNames(this, viewer)) return;
        if (displayNameRenderer.hidePlayerList(viewer, List.of(target))) {
            appliedHiddenPlayerLists.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>())
                    .put(target.getUniqueId(), target);
        }
    }

    /** Release one departing target without retaining its old Player instance. */
    public void removeHiddenPlayerListTarget(@NotNull Player viewer, @NotNull UUID targetId) {
        Map<UUID, Player> desired = desiredHiddenPlayerLists.get(viewer.getUniqueId());
        if (desired != null) desired.remove(targetId);
        Map<UUID, Player> applied = appliedHiddenPlayerLists.get(viewer.getUniqueId());
        Player target = applied == null ? null : applied.remove(targetId);
        if (target != null && viewer.isOnline()) displayNameRenderer.showPlayerList(viewer, List.of(target));
    }

    public void hidePlayersHealth() {
        healthEnabled = false;
        scoreboards.values().forEach(scoreboard -> {
            unregisterObjective(scoreboard, HEALTH_BELOW_OBJECTIVE);
            unregisterObjective(scoreboard, HEALTH_TAB_OBJECTIVE);
        });
    }

    public void showPlayersHealth(@NotNull SidebarLine line, boolean inTab) {
        healthLine = line;
        healthInTab = inTab;
        healthEnabled = true;
        scoreboards.values().forEach(this::renderHealth);
    }

    @NotNull
    public PlayerTab playerTabCreate(@NotNull String identifier, @NotNull Player player, @NotNull SidebarLine prefix,
                                     @NotNull SidebarLine suffix, @NotNull PlayerTab.PushingRule pushingRule,
                                     @NotNull ConcurrentLinkedQueue<PlaceholderProvider> placeholders) {
        return playerTabCreate(identifier, player, prefix, suffix, pushingRule, placeholders, ChatColor.WHITE);
    }

    @NotNull
    public PlayerTab playerTabCreate(@NotNull String identifier, @NotNull Player player, @NotNull SidebarLine prefix,
                                     @NotNull SidebarLine suffix, @NotNull PlayerTab.PushingRule pushingRule,
                                     @NotNull ConcurrentLinkedQueue<PlaceholderProvider> placeholders,
                                     @NotNull ChatColor color) {
        return playerTabCreate(identifier, player, prefix, suffix, pushingRule, placeholders, color,
                PlayerTab.NameTagVisibility.ALWAYS);
    }

    @NotNull
    public PlayerTab playerTabCreate(@NotNull String identifier, @NotNull Player player, @NotNull SidebarLine prefix,
                                     @NotNull SidebarLine suffix, @NotNull PlayerTab.PushingRule pushingRule,
                                     @NotNull ConcurrentLinkedQueue<PlaceholderProvider> placeholders,
                                     @NotNull ChatColor color,
                                     @NotNull PlayerTab.NameTagVisibility nameTagVisibility) {
        return playerTabCreate(identifier, player, prefix, suffix, pushingRule, placeholders, color,
                nameTagVisibility, PlayerTab.PlayerListMode.ACTUAL);
    }

    @NotNull
    public PlayerTab playerTabCreate(@NotNull String identifier, @NotNull Player player, @NotNull SidebarLine prefix,
                                     @NotNull SidebarLine suffix, @NotNull PlayerTab.PushingRule pushingRule,
                                     @NotNull ConcurrentLinkedQueue<PlaceholderProvider> placeholders,
                                     @NotNull ChatColor color,
                                     @NotNull PlayerTab.NameTagVisibility nameTagVisibility,
                                     @NotNull PlayerTab.PlayerListMode playerListMode) {
        return playerTabCreate(identifier, player, prefix, suffix, pushingRule, placeholders, color,
                nameTagVisibility, playerListMode, null);
    }

    /**
     * Creates a TAB row and optionally assigns it to a shared collision group.
     * The legacy overloads retain their private per-row scoreboard teams.
     */
    @NotNull
    public PlayerTab playerTabCreate(@NotNull String identifier, @NotNull Player player, @NotNull SidebarLine prefix,
                                     @NotNull SidebarLine suffix, @NotNull PlayerTab.PushingRule pushingRule,
                                     @NotNull ConcurrentLinkedQueue<PlaceholderProvider> placeholders,
                                     @NotNull ChatColor color,
                                     @NotNull PlayerTab.NameTagVisibility nameTagVisibility,
                                     @NotNull PlayerTab.PlayerListMode playerListMode,
                                     @Nullable String collisionGroup) {
        PlayerTab tab = new PlayerTab(identifier, player, prefix, suffix, pushingRule, placeholders,
                color, nameTagVisibility, playerListMode, collisionGroup);
        tab.setUpdateCallback(this::applyTabToAll);
        PlayerTab previous = tabs.put(identifier, tab);
        boolean forceDisplayName = previous != null;
        if (previous != null) {
            previous.setUpdateCallback(ignored -> {
            });
            removePlayerFromTabTeam(identifier, previous.getPlayer().getName());
            removeCollisionEntry(previous);
            if (!previous.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                restoreOrRenderRemainingTab(previous);
            }
        }
        applyTabToAll(tab, forceDisplayName);
        return tab;
    }

    public void removeTab(@NotNull String identifier) {
        PlayerTab tab = tabs.remove(identifier);
        if (tab == null) {
            return;
        }
        detachTab(tab);
        String teamName = tabTeamNames.remove(identifier);
        if (teamName != null) {
            scoreboards.values().forEach(scoreboard -> {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    team.unregister();
                }
            });
        }
        removeCollisionEntry(tab);
        restoreOrRenderRemainingTab(tab);
    }

    private void removePlayerFromTabTeam(@NotNull String identifier, @NotNull String playerName) {
        String teamName = tabTeamNames.get(identifier);
        if (teamName == null) return;
        scoreboards.values().forEach(scoreboard -> {
            Team team = scoreboard.getTeam(teamName);
            if (team != null && team.hasEntry(playerName)) {
                team.removeEntry(playerName);
            }
        });
    }

    private void removeCollisionEntry(@NotNull PlayerTab tab) {
        String group = tab.getCollisionGroup();
        if (group == null) return;
        String teamName = collisionTeamNames.get(group);
        if (teamName == null) return;
        scoreboards.values().forEach(scoreboard -> {
            Team team = scoreboard.getTeam(teamName);
            if (team != null && team.hasEntry(tab.getPlayer().getName())) {
                team.removeEntry(tab.getPlayer().getName());
            }
        });
        if (tabs.values().stream().noneMatch(other -> group.equals(other.getCollisionGroup()))) {
            collisionTeamNames.remove(group);
            scoreboards.values().forEach(scoreboard -> {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) team.unregister();
            });
        }
    }

    private void renderAll() {
        if (scoreboards.isEmpty()) {
            return;
        }
        RenderedSidebar rendered = renderSidebar();
        scoreboards.values().forEach(scoreboard -> render(scoreboard, rendered));
    }

    private void render(@NotNull Scoreboard scoreboard) {
        render(scoreboard, renderSidebar());
    }

    private void render(@NotNull Scoreboard scoreboard, @NotNull RenderedSidebar rendered) {
        removeLineTeams(scoreboard);
        unregisterObjective(scoreboard, SIDEBAR_OBJECTIVE);

        if (rendered.empty()) {
            return;
        }

        Objective objective = scoreboard.registerNewObjective(
                SIDEBAR_OBJECTIVE,
                Criteria.DUMMY,
                rendered.title()
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        int visibleLines = rendered.lines().size();
        for (int index = 0; index < visibleLines; index++) {
            RenderedSidebarLine line = rendered.lines().get(index);
            Team team = scoreboard.registerNewTeam(LINE_TEAM_PREFIX + index);
            team.addEntry(line.entry());
            team.prefix(line.text());

            Score score = objective.getScore(line.entry());
            score.setScore(visibleLines - index);
            if (line.numberFormat() != null) score.numberFormat(line.numberFormat());
        }
    }

    /**
     * Placeholder refreshes do not change the sidebar shape. Reuse the
     * existing objective and line teams so a final kill only sends the values
     * that actually changed. If another plugin removed part of our structure,
     * rebuild once to restore a complete, authoritative model.
     */
    private void refreshRenderedContent(@NotNull Scoreboard scoreboard,
                                        @NotNull RenderedSidebar rendered) {
        Objective objective = scoreboard.getObjective(SIDEBAR_OBJECTIVE);
        if (!hasCompleteStructure(scoreboard, objective, rendered)) {
            render(scoreboard, rendered);
            return;
        }
        if (rendered.empty()) return;

        if (!Objects.equals(objective.displayName(), rendered.title())) {
            objective.displayName(rendered.title());
        }
        for (int index = 0; index < rendered.lines().size(); index++) {
            RenderedSidebarLine line = rendered.lines().get(index);
            Team team = scoreboard.getTeam(LINE_TEAM_PREFIX + index);
            if (!Objects.equals(team.prefix(), line.text())) {
                team.prefix(line.text());
            }

            Score score = objective.getScore(line.entry());
            if (!sameNumberFormat(score.numberFormat(), line.numberFormat())) {
                score.numberFormat(line.numberFormat());
            }
        }
    }

    private static boolean hasCompleteStructure(@NotNull Scoreboard scoreboard,
                                                @Nullable Objective objective,
                                                @NotNull RenderedSidebar rendered) {
        if (rendered.empty()) {
            if (objective != null) return false;
            for (int index = 0; index < LINE_ENTRIES.length; index++) {
                if (scoreboard.getTeam(LINE_TEAM_PREFIX + index) != null) return false;
            }
            return true;
        }
        if (objective == null || objective.getDisplaySlot() != DisplaySlot.SIDEBAR) return false;

        int visibleLines = rendered.lines().size();
        for (int index = 0; index < LINE_ENTRIES.length; index++) {
            Team team = scoreboard.getTeam(LINE_TEAM_PREFIX + index);
            if (index >= visibleLines) {
                if (team != null) return false;
                continue;
            }
            RenderedSidebarLine line = rendered.lines().get(index);
            if (team == null || !team.hasEntry(line.entry())) return false;
            Score score = objective.getScore(line.entry());
            if (!score.isScoreSet() || score.getScore() != visibleLines - index) return false;
        }
        return true;
    }

    private @NotNull RenderedSidebar renderSidebar() {
        String renderedTitle = renderText(title, placeholders);
        int visibleLines = Math.min(lines.size(), LINE_ENTRIES.length);
        List<RenderedSidebarLine> renderedLines = new ArrayList<>(visibleLines);
        for (int index = 0; index < visibleLines; index++) {
            SidebarLine line = lines.get(index);
            NumberFormat numberFormat = null;
            if (line instanceof ScoredLine scoredLine && scoredLine.getScore() != null) {
                String fixedScore = replacePlaceholders(scoredLine.getScore(), placeholders);
                numberFormat = NumberFormat.fixed(component(fixedScore));
            }
            renderedLines.add(new RenderedSidebarLine(
                    LINE_ENTRIES[index], component(renderText(line, placeholders)), numberFormat));
        }
        return new RenderedSidebar(
                component(renderedTitle), renderedLines,
                renderedLines.isEmpty() && renderedTitle.isBlank());
    }

    private static boolean sameNumberFormat(@Nullable NumberFormat current,
                                            @Nullable NumberFormat expected) {
        if (current == expected) return true;
        if (current == null || expected == null) return false;
        if (current instanceof FixedFormat currentFixed
                && expected instanceof FixedFormat expectedFixed) {
            return currentFixed.component().equals(expectedFixed.component());
        }
        return current.equals(expected);
    }

    private record RenderedSidebar(@NotNull Component title,
                                   @NotNull List<RenderedSidebarLine> lines,
                                   boolean empty) {
    }

    private record RenderedSidebarLine(@NotNull String entry, @NotNull Component text,
                                       @Nullable NumberFormat numberFormat) {
    }

    private void renderHealth(@NotNull Scoreboard scoreboard) {
        if (!healthEnabled) {
            return;
        }
        Objective belowName = getOrCreateObjective(scoreboard, HEALTH_BELOW_OBJECTIVE, DisplaySlot.BELOW_NAME);
        belowName.numberFormat(NumberFormat.noStyle());

        if (healthInTab) {
            Objective tab = getOrCreateObjective(scoreboard, HEALTH_TAB_OBJECTIVE, DisplaySlot.PLAYER_LIST);
            tab.numberFormat(NumberFormat.noStyle());
        } else {
            unregisterObjective(scoreboard, HEALTH_TAB_OBJECTIVE);
        }
        renderHealthTitle(scoreboard);
    }

    private void renderHealthTitle(@NotNull Scoreboard scoreboard) {
        if (!healthEnabled) {
            return;
        }
        Component title = component(renderText(healthLine, placeholders));
        Objective belowName = scoreboard.getObjective(HEALTH_BELOW_OBJECTIVE);
        if (belowName != null) {
            belowName.displayName(title);
        }
        Objective tab = scoreboard.getObjective(HEALTH_TAB_OBJECTIVE);
        if (tab != null) {
            tab.displayName(title);
        }
    }

    private Objective getOrCreateObjective(@NotNull Scoreboard scoreboard, @NotNull String name, @NotNull DisplaySlot slot) {
        Objective objective = scoreboard.getObjective(name);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(name, Criteria.DUMMY, component(""));
        }
        objective.setDisplaySlot(slot);
        return objective;
    }

    private void applyTabToAll(@NotNull PlayerTab tab) {
        applyTabToAll(tab, false);
    }

    private void applyTabToAll(@NotNull PlayerTab tab, boolean forceDisplayName) {
        RenderedPlayerTab renderedTab = renderPlayerTab(tab);
        List<Player> detachedViewers = new ArrayList<>();
        scoreboards.forEach((viewerId, scoreboard) -> {
            Player viewer = viewers.get(viewerId);
            if (viewer != null && viewer.isOnline()) {
                boolean ownsDisplayNames = SidebarManager.getInstance().ownsDisplayNames(this, viewer);
                if (ownsDisplayNames
                        && shouldReattachScoreboard(true, scoreboard, viewer.getScoreboard())) {
                    detachedViewers.add(viewer);
                    return;
                }
                applyTab(scoreboard, renderedTab);
                if (ownsDisplayNames) {
                    renderPlayerListNames(viewer, List.of(renderedTab), forceDisplayName);
                }
            }
        });
        detachedViewers.forEach(this::add);
    }

    void applyTab(@NotNull Scoreboard scoreboard, @NotNull PlayerTab tab) {
        applyTab(scoreboard, renderPlayerTab(tab));
    }

    private void applyTab(@NotNull Scoreboard scoreboard, @NotNull RenderedPlayerTab renderedTab) {
        PlayerTab tab = renderedTab.tab();
        boolean pushOtherTeams = tab.getPushingRule() == PlayerTab.PushingRule.PUSH_OTHER_TEAMS;
        boolean sharedCollision = tab.getCollisionGroup() != null && pushOtherTeams;
        String scoreboardTeamName = sharedCollision
                ? collisionTeamName(tab.getCollisionGroup()) : teamName(tab.getIdentifier());
        Team team = scoreboard.getTeam(scoreboardTeamName);
        boolean newTeam = team == null;
        if (team == null) {
            team = scoreboard.registerNewTeam(scoreboardTeamName);
        }

        // A shared collision team cannot carry per-player suffixes. Its TAB
        // display name is sent separately, so keep the first row's name-tag
        // formatting and avoid overwriting it on every player's refresh.
        if (!sharedCollision || newTeam) {
            Component prefix = component(renderedTab.prefix());
            if (!team.prefix().equals(prefix)) team.prefix(prefix);
            Component suffix = component(sharedCollision ? "" : renderedTab.suffix());
            if (!team.suffix().equals(suffix)) team.suffix(suffix);
        }

        // Collision teams are shared by several rows. Their prefix/suffix is
        // intentionally written only once, but the colour must be refreshed
        // for every row update: a row can be created before its arena team is
        // assigned and later reuse the same collision team.
        applyTeamColor(team, tab);

        Team.OptionStatus visibility = sharedCollision
                ? Team.OptionStatus.ALWAYS
                : tab.getNameTagVisibility() == PlayerTab.NameTagVisibility.NEVER
                ? Team.OptionStatus.NEVER
                : Team.OptionStatus.ALWAYS;
        if ((!sharedCollision || newTeam)
                && team.getOption(Team.Option.NAME_TAG_VISIBILITY) != visibility) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, visibility);
        }
        // A shared collision team may already exist from an earlier row. Its
        // collision rule still has to be repaired when a scoreboard is reused.
        Team.OptionStatus collision = pushOtherTeams
                ? Team.OptionStatus.FOR_OTHER_TEAMS
                : tab.getCollisionGroup() != null
                ? Team.OptionStatus.NEVER
                : tab.getPlayerListMode() == PlayerTab.PlayerListMode.ACTUAL
                ? Team.OptionStatus.ALWAYS
                : null;
        if (collision != null && team.getOption(Team.Option.COLLISION_RULE) != collision) {
            team.setOption(Team.Option.COLLISION_RULE, collision);
        }
        if (!team.hasEntry(tab.getPlayer().getName())) {
            team.addEntry(tab.getPlayer().getName());
        }

    }

    void renderPlayerListName(@NotNull Player viewer, @NotNull PlayerTab tab) {
        renderPlayerListNames(viewer, List.of(renderPlayerTab(tab)), false);
    }

    void forcePlayerListNameRefresh(@NotNull Player viewer) {
        Scoreboard managedScoreboard = scoreboards.get(viewer.getUniqueId());
        if (managedScoreboard != null
                && SidebarManager.getInstance().ownsDisplayNames(this, viewer)
                && shouldReattachScoreboard(true, managedScoreboard, viewer.getScoreboard())) {
            add(viewer);
            return;
        }
        retryPendingPlayerListRestores(viewer);
        renderPlayerListNames(viewer, renderPlayerTabs(tabs.values()), true);
    }

    void suspendDisplayNameOwnership(@NotNull Player viewer) {
        if (!viewer.isOnline()) {
            discardViewerDisplayNameState(viewer.getUniqueId());
            return;
        }
        retryPendingPlayerListRestores(viewer);
        restorePlayerListNames(viewer, tabs.values());
        releaseHiddenPlayerList(viewer);
    }

    void resumeDisplayNameOwnership(@NotNull Player viewer) {
        if (!viewer.isOnline() || !SidebarManager.getInstance().ownsDisplayNames(this, viewer)) return;
        Scoreboard managedScoreboard = scoreboards.get(viewer.getUniqueId());
        if (managedScoreboard != null
                && shouldReattachScoreboard(true, managedScoreboard, viewer.getScoreboard())) {
            add(viewer);
            return;
        }
        retryPendingPlayerListRestores(viewer);
        renderPlayerListNames(viewer, renderPlayerTabs(tabs.values()), true);
        applyHiddenPlayerListState(viewer);
    }

    private void renderPlayerListNames(@NotNull Player viewer, @NotNull Collection<RenderedPlayerTab> playerTabs,
                                       boolean force) {
        Set<UUID> confirmedNames = renderExplicitPlayerListNames(viewer, playerTabs, force);
        Set<UUID> confirmedModes = renderPlayerListGameModes(viewer, playerTabs, force);
        confirmedNames.stream()
                .filter(confirmedModes::contains)
                .forEach(targetId -> removePendingPlayerListRestore(viewer.getUniqueId(), targetId));
    }

    private @NotNull Set<UUID> renderExplicitPlayerListNames(
            @NotNull Player viewer, @NotNull Collection<RenderedPlayerTab> playerTabs, boolean force) {
        UUID viewerId = viewer.getUniqueId();
        Map<UUID, String> viewerCache = renderedPlayerListNames.computeIfAbsent(
                viewerId, ignored -> new HashMap<>());
        Map<UUID, RenderedPlayerTab> targets = new LinkedHashMap<>();
        for (RenderedPlayerTab renderedTab : playerTabs) {
            Player target = renderedTab.tab().getPlayer();
            targets.putIfAbsent(target.getUniqueId(), renderedTab);
        }
        List<PlayerListDisplayNameRenderer.RenderedName> updates = new ArrayList<>();
        Set<UUID> confirmed = new HashSet<>();
        for (Map.Entry<UUID, RenderedPlayerTab> entry : targets.entrySet()) {
            UUID targetId = entry.getKey();
            RenderedPlayerTab renderedTab = entry.getValue();
            Player target = renderedTab.tab().getPlayer();
            if (!target.isOnline()) {
                releaseCachedPlayerListState(viewerId, targetId);
                continue;
            }
            if (!force && !hasPendingPlayerListRestore(viewerId, targetId)
                    && renderedTab.displayName().equals(viewerCache.get(targetId))) {
                confirmed.add(targetId);
                continue;
            }
            updates.add(new PlayerListDisplayNameRenderer.RenderedName(target, renderedTab.displayName()));
        }
        if (!updates.isEmpty() && displayNameRenderer.render(viewer, updates)) {
            updates.forEach(update -> {
                UUID targetId = update.target().getUniqueId();
                viewerCache.put(targetId, update.legacyDisplayName());
                confirmed.add(targetId);
            });
        }
        return confirmed;
    }

    private @NotNull Set<UUID> renderPlayerListGameModes(
            @NotNull Player viewer, @NotNull Collection<RenderedPlayerTab> playerTabs, boolean force) {
        UUID viewerId = viewer.getUniqueId();
        Set<UUID> spectatorCache = spectatorPlayerListModes.computeIfAbsent(
                viewerId, ignored -> new HashSet<>());
        LinkedHashMap<UUID, Player> targets = new LinkedHashMap<>();
        Set<UUID> desiredSpectators = new HashSet<>();
        for (RenderedPlayerTab renderedTab : playerTabs) {
            PlayerTab tab = renderedTab.tab();
            Player target = tab.getPlayer();
            UUID targetId = target.getUniqueId();
            targets.putIfAbsent(targetId, target);
            if (tab.getPlayerListMode() == PlayerTab.PlayerListMode.SPECTATOR) {
                desiredSpectators.add(targetId);
            }
        }
        // An API consumer can expose the same player through more than one
        // identifier. Incremental updates render only the changed row, so
        // include the other managed rows before deciding which mode wins.
        for (PlayerTab managedTab : tabs.values()) {
            UUID targetId = managedTab.getPlayer().getUniqueId();
            if (targets.containsKey(targetId)
                    && managedTab.getPlayerListMode() == PlayerTab.PlayerListMode.SPECTATOR) {
                desiredSpectators.add(targetId);
            }
        }

        List<Player> setSpectator = new ArrayList<>();
        List<Player> restoreActual = new ArrayList<>();
        Set<UUID> confirmed = new HashSet<>();
        for (Map.Entry<UUID, Player> entry : targets.entrySet()) {
            UUID targetId = entry.getKey();
            Player target = entry.getValue();
            if (!target.isOnline()) {
                releaseCachedPlayerListState(viewerId, targetId);
                continue;
            }
            // UPDATE_GAME_MODE for the viewer's own PlayerInfo entry is not a
            // cosmetic-only change. The vanilla 1.21.11 client invokes
            // LocalPlayer#onGameModeChanged for that UUID, which can alter
            // movement even though Bukkit still reports the real mode. Keep
            // self state authoritative; other rows can safely use the
            // spectator value as TAB-only presentation.
            boolean spectatorPresentation = desiredSpectators.contains(targetId)
                    && !viewerId.equals(targetId);
            if (spectatorPresentation) {
                if (force || hasPendingPlayerListRestore(viewerId, targetId)
                        || !spectatorCache.contains(targetId)) {
                    setSpectator.add(target);
                } else {
                    confirmed.add(targetId);
                }
            } else if (spectatorCache.contains(targetId)) {
                restoreActual.add(target);
            } else {
                confirmed.add(targetId);
            }
        }

        if (!setSpectator.isEmpty() && displayNameRenderer.setSpectatorMode(viewer, setSpectator)) {
            setSpectator.forEach(target -> {
                spectatorCache.add(target.getUniqueId());
                confirmed.add(target.getUniqueId());
            });
        }
        if (!restoreActual.isEmpty() && displayNameRenderer.restoreGameMode(viewer, restoreActual)) {
            restoreActual.forEach(target -> {
                spectatorCache.remove(target.getUniqueId());
                confirmed.add(target.getUniqueId());
            });
        }
        return confirmed;
    }

    void restorePlayerListName(@NotNull Player viewer, @NotNull PlayerTab tab) {
        restorePlayerListNames(viewer, List.of(tab));
    }

    private void restorePlayerListNames(@NotNull Player viewer, @NotNull Collection<PlayerTab> playerTabs) {
        List<Player> targets = playerTabs.stream().map(PlayerTab::getPlayer).toList();
        restorePlayerListTargets(viewer, targets);
    }

    private void retryPendingPlayerListRestores(@NotNull Player viewer) {
        Map<UUID, Player> pending = pendingPlayerListRestores.get(viewer.getUniqueId());
        if (pending == null || pending.isEmpty()) return;
        restorePlayerListTargets(viewer, new ArrayList<>(pending.values()));
    }

    private void restorePlayerListTargets(@NotNull Player viewer, @NotNull Collection<Player> candidates) {
        UUID viewerId = viewer.getUniqueId();
        Map<UUID, String> nameCache = renderedPlayerListNames.get(viewerId);
        Set<UUID> modeCache = spectatorPlayerListModes.get(viewerId);
        if (nameCache == null && modeCache == null) return;
        Map<UUID, Player> targets = new LinkedHashMap<>();
        for (Player target : candidates) {
            UUID targetId = target.getUniqueId();
            boolean hasNameState = nameCache != null && nameCache.containsKey(targetId);
            boolean hasModeState = modeCache != null && modeCache.contains(targetId);
            if (!hasNameState && !hasModeState) continue;
            if (target.isOnline()) {
                targets.putIfAbsent(targetId, target);
            } else {
                // Paper has already removed an offline player's PlayerInfo row,
                // so there is nothing left to restore on the client. Still
                // release our per-viewer cache entry to avoid retaining every
                // player that has ever passed through a long-running lobby.
                releaseCachedPlayerListState(viewerId, targetId);
            }
        }
        if (targets.isEmpty()) return;
        if (displayNameRenderer.restore(viewer, targets.values())) {
            targets.keySet().forEach(targetId -> releaseCachedPlayerListState(viewerId, targetId));
            return;
        }
        pendingPlayerListRestores.computeIfAbsent(viewerId, ignored -> new LinkedHashMap<>())
                .putAll(targets);
    }

    private void releaseCachedPlayerListState(@NotNull UUID viewerId, @NotNull UUID targetId) {
        Map<UUID, String> nameCache = renderedPlayerListNames.get(viewerId);
        if (nameCache != null) nameCache.remove(targetId);
        Set<UUID> modeCache = spectatorPlayerListModes.get(viewerId);
        if (modeCache != null) modeCache.remove(targetId);
        removePendingPlayerListRestore(viewerId, targetId);
    }

    private void removePendingPlayerListRestore(@NotNull UUID viewerId, @NotNull UUID targetId) {
        Map<UUID, Player> pending = pendingPlayerListRestores.get(viewerId);
        if (pending == null) return;
        pending.remove(targetId);
        if (pending.isEmpty()) pendingPlayerListRestores.remove(viewerId);
    }

    private boolean hasPendingPlayerListRestore(@NotNull UUID viewerId, @NotNull UUID targetId) {
        Map<UUID, Player> pending = pendingPlayerListRestores.get(viewerId);
        return pending != null && pending.containsKey(targetId);
    }

    private void discardViewerDisplayNameState(@NotNull UUID viewerId) {
        renderedPlayerListNames.remove(viewerId);
        spectatorPlayerListModes.remove(viewerId);
        pendingPlayerListRestores.remove(viewerId);
        desiredHiddenPlayerLists.remove(viewerId);
        appliedHiddenPlayerLists.remove(viewerId);
    }

    private void restoreOrRenderRemainingTab(@NotNull PlayerTab removedTab) {
        PlayerTab remaining = tabs.values().stream()
                .filter(tab -> tab.getPlayer().getUniqueId().equals(removedTab.getPlayer().getUniqueId()))
                .findFirst()
                .orElse(null);
        if (remaining != null) {
            applyTabToAll(remaining, true);
            return;
        }
        viewers.values().stream()
                .filter(Player::isOnline)
                .filter(viewer -> SidebarManager.getInstance().ownsDisplayNames(this, viewer))
                .forEach(viewer -> restorePlayerListNames(viewer, List.of(removedTab)));
    }

    private static @NotNull List<RenderedPlayerTab> renderPlayerTabs(
            @NotNull Collection<PlayerTab> playerTabs) {
        return playerTabs.stream().map(Sidebar::renderPlayerTab).toList();
    }

    private static @NotNull RenderedPlayerTab renderPlayerTab(@NotNull PlayerTab tab) {
        String prefix = renderText(tab.getPrefix(), tab.getPlaceholders());
        String suffix = renderText(tab.getSuffix(), tab.getPlaceholders());
        Component playerName = Component.text(tab.getPlayer().getName()).color(tab.getTextColor());
        if (tab.isItalic()) playerName = playerName.decorate(TextDecoration.ITALIC);
        Component displayName = component(prefix)
                .append(playerName)
                .append(component(suffix));
        return new RenderedPlayerTab(tab, prefix, suffix,
                LEGACY.serialize(displayName));
    }

    private static void applyTeamColor(@NotNull Team team, @NotNull PlayerTab tab) {
        /*
         * Paper 1.21.11's Team.color() getter only accepts a scoreboard color
         * backed by a hex value. A newly registered team has the RESET color,
         * which therefore throws "Team colors must have hex values" while reading.
         * The deprecated getter is the only safe read path for those named
         * colors; keep the modern Adventure setter for the actual update.
         */
        boolean changed;
        try {
            changed = !Objects.equals(team.getColor(), tab.getColor());
        } catch (UnsupportedOperationException ignored) {
            changed = true;
        }
        if (!changed) return;

        try {
            team.color(tab.getTextColor());
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            // Compatibility for old scoreboard implementations and test doubles.
            team.setColor(tab.getColor());
        }
    }

    private record RenderedPlayerTab(@NotNull PlayerTab tab, @NotNull String prefix,
                                     @NotNull String suffix, @NotNull String displayName) {
    }

    private static void unregisterObjective(@NotNull Scoreboard scoreboard, @NotNull String name) {
        Objective objective = scoreboard.getObjective(name);
        if (objective != null) {
            objective.unregister();
        }
    }

    private static void removeLineTeams(@NotNull Scoreboard scoreboard) {
        for (int i = 0; i < LINE_ENTRIES.length; i++) {
            Team team = scoreboard.getTeam(LINE_TEAM_PREFIX + i);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private static void removeTabTeams(@NotNull Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(TAB_TEAM_PREFIX)
                    || team.getName().startsWith(COLLISION_TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    String teamName(@NotNull String identifier) {
        // A per-sidebar monotonic id is stable and cannot collide. String.hashCode based names
        // could merge two unrelated players into one scoreboard team and corrupt client state.
        return tabTeamNames.computeIfAbsent(identifier,
                ignored -> TAB_TEAM_PREFIX + Integer.toString(nextTabTeamId++, Character.MAX_RADIX));
    }

    String collisionTeamName(@NotNull String group) {
        return collisionTeamNames.computeIfAbsent(group,
                ignored -> COLLISION_TEAM_PREFIX + Integer.toString(nextCollisionTeamId++, Character.MAX_RADIX));
    }

    static boolean shouldCapturePreviousScoreboard(Scoreboard managedScoreboard,
                                                    @NotNull Scoreboard currentScoreboard) {
        return managedScoreboard != currentScoreboard;
    }

    static boolean shouldReattachScoreboard(boolean ownsDisplayNames, @Nullable Scoreboard managedScoreboard,
                                             @NotNull Scoreboard currentScoreboard) {
        return ownsDisplayNames && managedScoreboard != null && managedScoreboard != currentScoreboard;
    }

    private static void detachTab(@NotNull PlayerTab tab) {
        tab.setUpdateCallback(ignored -> {
        });
    }

    static String renderText(@NotNull SidebarLine line, @NotNull Collection<PlaceholderProvider> placeholders) {
        return replacePlaceholders(line.getLine(), placeholders);
    }

    static String replacePlaceholders(String text, @NotNull Collection<PlaceholderProvider> placeholders) {
        String result = text == null ? "" : text;
        for (PlaceholderProvider placeholder : placeholders) {
            if (!result.contains(placeholder.getPlaceholder())) continue;
            String value = placeholder.getValue();
            result = result.replace(placeholder.getPlaceholder(), value == null ? "" : value);
        }
        return AdventureText.section(AdventureText.ampersand(result));
    }

    static Component component(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText);
    }
}
