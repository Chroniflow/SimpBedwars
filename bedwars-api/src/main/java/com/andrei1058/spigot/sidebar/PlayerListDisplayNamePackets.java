package com.andrei1058.spigot.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Paper 1.21.11 player-list packet bridge. Reflection keeps NMS out of the
 * published API artifact while all reflective members are resolved only once.
 */
final class PlayerListDisplayNamePackets implements PlayerListDisplayNameRenderer {

    private static final PlayerListDisplayNameRenderer INSTANCE = new LazyRenderer();

    private final Method craftPlayerGetHandle;
    private final Field serverPlayerConnection;
    private final Method connectionSend;
    private final Method legacyComponent;
    private final Method packetActions;
    private final Method packetEntries;
    private final Method entryProfileId;
    private final Method entryGameMode;
    private final Method entryDisplayName;
    private final Constructor<?> snapshotPacket;
    private final Constructor<?> entryConstructor;
    private final Constructor<?> entriesPacket;
    private final EnumSet<?> displayNameAction;
    private final EnumSet<?> gameModeAction;
    private final EnumSet<?> restoreActions;
    private final EnumSet<?> listedAction;
    private final Object spectatorGameType;
    private boolean packetFailureLogged;

    private PlayerListDisplayNamePackets() throws ReflectiveOperationException {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> playerInfoPacket = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> playerInfoEntry = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> playerInfoAction = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Class<?> chatSessionData = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
        Class<?> connection = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
        craftPlayerGetHandle = craftPlayer.getMethod("getHandle");
        serverPlayerConnection = serverPlayer.getField("connection");
        connectionSend = connection.getMethod("send", packet);
        legacyComponent = craftChatMessage.getMethod("fromStringOrEmpty", String.class);
        snapshotPacket = playerInfoPacket.getConstructor(EnumSet.class, Collection.class);
        entryConstructor = playerInfoEntry.getConstructor(
                UUID.class, gameProfile, boolean.class, int.class, gameType,
                component, boolean.class, int.class, chatSessionData);
        entriesPacket = playerInfoPacket.getConstructor(EnumSet.class, List.class);
        packetActions = playerInfoPacket.getMethod("actions");
        packetEntries = playerInfoPacket.getMethod("entries");
        entryProfileId = playerInfoEntry.getMethod("profileId");
        entryGameMode = playerInfoEntry.getMethod("gameMode");
        entryDisplayName = playerInfoEntry.getMethod("displayName");
        displayNameAction = actions(playerInfoAction, "UPDATE_DISPLAY_NAME");
        gameModeAction = actions(playerInfoAction, "UPDATE_GAME_MODE");
        restoreActions = actions(playerInfoAction, "UPDATE_DISPLAY_NAME", "UPDATE_GAME_MODE");
        listedAction = actions(playerInfoAction, "UPDATE_LISTED");
        spectatorGameType = enumConstant(gameType, "SPECTATOR");
        verifyPacketConstruction();
    }

    static @NotNull PlayerListDisplayNameRenderer instance() {
        return INSTANCE;
    }

    static void verifyCompatibility() {
        if (INSTANCE instanceof LazyRenderer lazyRenderer) lazyRenderer.delegate();
    }

    static boolean isCompatible() {
        PlayerListDisplayNameRenderer renderer = INSTANCE instanceof LazyRenderer lazyRenderer
                ? lazyRenderer.delegate()
                : INSTANCE;
        return renderer != UnavailableRenderer.INSTANCE;
    }

    @Override
    public boolean render(@NotNull Player viewer, @NotNull Collection<RenderedName> names) {
        if (names.isEmpty()) return true;
        try {
            send(viewer, createDisplayNamePacket(names));
            return true;
        } catch (ReflectiveOperationException exception) {
            logPacketFailure(viewer, exception);
            return false;
        }
    }

    private Object createDisplayNamePacket(Collection<RenderedName> names) throws ReflectiveOperationException {
        ArrayList<Object> entries = new ArrayList<>(names.size());
        for (RenderedName name : names) {
            Object renderedName = legacyComponent.invoke(null, name.legacyDisplayName());
            entries.add(entryConstructor.newInstance(
                    name.target().getUniqueId(), null, false, 0, null,
                    renderedName, false, 0, null));
        }
        return entriesPacket.newInstance(displayNameAction, entries);
    }

    @Override
    public boolean setSpectatorMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
        if (targets.isEmpty()) return true;
        try {
            ArrayList<Object> entries = new ArrayList<>(targets.size());
            for (Player target : targets) {
                entries.add(entryConstructor.newInstance(
                        target.getUniqueId(), null, false, 0, spectatorGameType,
                        null, false, 0, null));
            }
            send(viewer, entriesPacket.newInstance(gameModeAction, entries));
            return true;
        } catch (ReflectiveOperationException exception) {
            logPacketFailure(viewer, exception);
            return false;
        }
    }

    @Override
    public boolean restoreGameMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
        return sendSnapshot(viewer, targets, gameModeAction);
    }

    @Override
    public boolean restore(@NotNull Player viewer, @NotNull Collection<Player> targets) {
        return sendSnapshot(viewer, targets, restoreActions);
    }

    @Override
    public boolean hidePlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
        return setListed(viewer, targets, false);
    }

    @Override
    public boolean showPlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
        // Restore only server-listed, visible players. UPDATE_LISTED never
        // recreates a removed profile or exposes an offline/vanished player.
        return setListed(viewer, targets.stream().filter(Player::isOnline)
                .filter(viewer::canSee).filter(viewer::isListed).toList(), true);
    }

    private boolean setListed(Player viewer, Collection<Player> targets, boolean listed) {
        if (targets.isEmpty()) return true;
        try {
            send(viewer, createListedPacket(targets, listed));
            return true;
        } catch (ReflectiveOperationException exception) {
            logPacketFailure(viewer, exception);
            return false;
        }
    }

    private Object createListedPacket(Collection<Player> targets, boolean listed)
            throws ReflectiveOperationException {
        List<Object> entries = new ArrayList<>(targets.size());
        for (Player target : targets) {
            entries.add(entryConstructor.newInstance(target.getUniqueId(), null, listed,
                    0, null, null, false, 0, null));
        }
        return entriesPacket.newInstance(listedAction, entries);
    }

    private boolean sendSnapshot(@NotNull Player viewer, @NotNull Collection<Player> targets,
                                 @NotNull EnumSet<?> packetActions) {
        if (targets.isEmpty()) return true;
        try {
            ArrayList<Object> targetHandles = new ArrayList<>(targets.size());
            for (Player target : targets) targetHandles.add(craftPlayerGetHandle.invoke(target));
            // The normal packet constructor reads Paper's current nullable
            // player-list name and real game mode, so third-party state is
            // restored rather than replaced with a stale snapshot.
            Object packet = snapshotPacket.newInstance(packetActions, targetHandles);
            send(viewer, packet);
            return true;
        } catch (ReflectiveOperationException exception) {
            logPacketFailure(viewer, exception);
            return false;
        }
    }

    private void send(Player viewer, Object packet) throws ReflectiveOperationException {
        Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
        Object connection = serverPlayerConnection.get(viewerHandle);
        connectionSend.invoke(connection, packet);
    }

    /**
     * Construct and inspect the exact packet shape during plugin startup. Merely
     * resolving reflective members would not catch a constructor whose fields
     * no longer represent UPDATE_DISPLAY_NAME in the supported Paper build.
     */
    private void verifyPacketConstruction() throws ReflectiveOperationException {
        UUID expectedId = new UUID(0L, 1L);
        Object expectedDisplayName = legacyComponent.invoke(null, "\u00a7cSimpMC");
        Object displayEntry = entryConstructor.newInstance(
                expectedId, null, false, 0, null, expectedDisplayName, false, 0, null);
        Object displayPacket = entriesPacket.newInstance(displayNameAction, List.of(displayEntry));

        Object displayActions = packetActions.invoke(displayPacket);
        Object displayEntries = packetEntries.invoke(displayPacket);
        if (!(displayActions instanceof Collection<?> packetActionValues)
                || !packetActionValues.containsAll(displayNameAction)
                || !(displayEntries instanceof List<?> packetEntryValues)
                || packetEntryValues.size() != 1
                || !expectedId.equals(entryProfileId.invoke(packetEntryValues.getFirst()))
                || entryDisplayName.invoke(packetEntryValues.getFirst()) != expectedDisplayName) {
            throw new ReflectiveOperationException(
                    "Paper 1.21.11 PlayerInfo UPDATE_DISPLAY_NAME packet contract changed");
        }

        Object spectatorEntry = entryConstructor.newInstance(
                expectedId, null, false, 0, spectatorGameType, null, false, 0, null);
        Object spectatorPacket = entriesPacket.newInstance(gameModeAction, List.of(spectatorEntry));
        Object spectatorActions = packetActions.invoke(spectatorPacket);
        Object spectatorEntries = packetEntries.invoke(spectatorPacket);
        if (!(spectatorActions instanceof Collection<?> spectatorActionValues)
                || !spectatorActionValues.containsAll(gameModeAction)
                || !(spectatorEntries instanceof List<?> spectatorEntryValues)
                || spectatorEntryValues.size() != 1
                || !expectedId.equals(entryProfileId.invoke(spectatorEntryValues.getFirst()))
                || entryGameMode.invoke(spectatorEntryValues.getFirst()) != spectatorGameType) {
            throw new ReflectiveOperationException(
                    "Paper 1.21.11 PlayerInfo UPDATE_GAME_MODE packet contract changed");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EnumSet<?> actions(Class<?> actionType, String... names) {
        Class<? extends Enum> enumType = actionType.asSubclass(Enum.class);
        EnumSet actions = EnumSet.noneOf(enumType);
        for (String name : names) actions.add(Enum.valueOf(enumType, name));
        return actions;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf(enumType.asSubclass(Enum.class), name);
    }

    private void logPacketFailure(Player viewer, Exception cause) {
        if (packetFailureLogged) return;
        packetFailureLogged = true;
        Bukkit.getLogger().log(Level.SEVERE, "SimpMC-BedWars 无法更新 " + viewer.getName()
                + " 看到的 TAB 名称或观察者状态；已保留 scoreboard 降级显示。", cause);
    }

    private static final class LazyRenderer implements PlayerListDisplayNameRenderer {
        private volatile PlayerListDisplayNameRenderer delegate;

        @Override
        public boolean render(@NotNull Player viewer, @NotNull Collection<RenderedName> names) {
            return delegate().render(viewer, names);
        }

        @Override
        public boolean setSpectatorMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return delegate().setSpectatorMode(viewer, targets);
        }

        @Override
        public boolean restoreGameMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return delegate().restoreGameMode(viewer, targets);
        }

        @Override
        public boolean restore(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return delegate().restore(viewer, targets);
        }

        @Override
        public boolean hidePlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return delegate().hidePlayerList(viewer, targets);
        }

        @Override
        public boolean showPlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return delegate().showPlayerList(viewer, targets);
        }

        private PlayerListDisplayNameRenderer delegate() {
            PlayerListDisplayNameRenderer current = delegate;
            if (current != null) return current;
            synchronized (this) {
                if (delegate == null) delegate = createRenderer();
                return delegate;
            }
        }

        private static PlayerListDisplayNameRenderer createRenderer() {
            try {
                return new PlayerListDisplayNamePackets();
            } catch (ReflectiveOperationException exception) {
                Bukkit.getLogger().log(Level.SEVERE,
                        "SimpMC-BedWars 无法初始化 Paper 1.21.11 TAB 数据包支持；"
                                + "玩家名颜色和观察者样式不会完整生效。",
                        exception);
                return UnavailableRenderer.INSTANCE;
            }
        }
    }

    private enum UnavailableRenderer implements PlayerListDisplayNameRenderer {
        INSTANCE;

        @Override
        public boolean render(@NotNull Player viewer, @NotNull Collection<RenderedName> names) {
            return false;
        }

        @Override
        public boolean setSpectatorMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return false;
        }

        @Override
        public boolean restoreGameMode(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return false;
        }

        @Override
        public boolean restore(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return false;
        }

        @Override
        public boolean hidePlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return false;
        }

        @Override
        public boolean showPlayerList(@NotNull Player viewer, @NotNull Collection<Player> targets) {
            return false;
        }
    }
}
