package com.andrei1058.spigot.sidebar;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Controls the PlayerInfo display name for one viewer.
 */
interface PlayerListDisplayNameRenderer {

    boolean render(@NotNull Player viewer, @NotNull Collection<RenderedName> names);

    /** Advertise spectator mode for these rows without changing Bukkit game mode. */
    boolean setSpectatorMode(@NotNull Player viewer, @NotNull Collection<Player> targets);

    /** Restore the targets' current server-side game mode for this viewer. */
    boolean restoreGameMode(@NotNull Player viewer, @NotNull Collection<Player> targets);

    /** Restore both server-owned display names and real game modes. */
    boolean restore(@NotNull Player viewer, @NotNull Collection<Player> targets);

    /** Hide these targets from this viewer's client-side PlayerInfo list. */
    boolean hidePlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets);

    /** Restore listing for targets that remain visible and listed on the server. */
    boolean showPlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets);

    record RenderedName(@NotNull Player target, @NotNull String legacyDisplayName) {
    }
}
