package com.andrei1058.spigot.sidebar;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Runs in CI with the real patched Paper 1.21.11 server and its libraries on
 * the classpath. This is deliberately a standalone probe: regular unit tests
 * use only paper-api and must remain independent from NMS.
 */
public final class PlayerListDisplayNamePacketsRuntimeProbe {

    private PlayerListDisplayNamePacketsRuntimeProbe() {
    }

    public static void main(String[] args) {
        bootstrapMinecraftRegistries();
        PlayerListDisplayNamePackets.verifyCompatibility();
        if (!PlayerListDisplayNamePackets.isCompatible()) {
            throw new IllegalStateException(
                    "Paper 1.21.11 TAB display-name/game-mode packet bridge is unavailable");
        }
        verifyColorOnWire(ChatColor.RED, false, "\u00a7cAlice");
        verifyColorOnWire(ChatColor.BLUE, true, "\u00a79\u00a7oAlice");
        verifyListingOnWire(false);
        verifyListingOnWire(true);
    }

    private static void verifyListingOnWire(boolean listed) {
        try {
            UUID playerId = UUID.randomUUID();
            Player target = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getUniqueId")) return playerId;
                        throw new UnsupportedOperationException(method.getName());
                    });
            var constructor = PlayerListDisplayNamePackets.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object bridge = constructor.newInstance();
            Method create = PlayerListDisplayNamePackets.class.getDeclaredMethod(
                    "createListedPacket", Collection.class, boolean.class);
            create.setAccessible(true);
            Object decoded = roundTrip(create.invoke(bridge, List.of(target), listed));
            Object entry = ((List<?>) decoded.getClass().getMethod("entries").invoke(decoded)).getFirst();
            Collection<?> actions = (Collection<?>) decoded.getClass().getMethod("actions").invoke(decoded);
            if (!playerId.equals(entry.getClass().getMethod("profileId").invoke(entry))
                    || !Boolean.valueOf(listed).equals(entry.getClass().getMethod("listed").invoke(entry))
                    || actions.size() != 1 || !actions.iterator().next().toString().equals("UPDATE_LISTED")) {
                throw new IllegalStateException("Paper PlayerInfo codec lost TAB visibility");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to verify TAB visibility on the Paper wire codec", exception);
        }
    }

    private static Object roundTrip(Object packet) throws ReflectiveOperationException {
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf");
        Object rawBuffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        try {
            Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess");
            Object buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf")
                    .getConstructor(byteBuf, registryAccess)
                    .newInstance(rawBuffer, registryAccess.getField("EMPTY").get(null));
            Object codec = packet.getClass().getField("STREAM_CODEC").get(null);
            Class.forName("net.minecraft.network.codec.StreamEncoder")
                    .getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
            return Class.forName("net.minecraft.network.codec.StreamDecoder")
                    .getMethod("decode", Object.class).invoke(codec, buffer);
        } finally {
            byteBuf.getMethod("release").invoke(rawBuffer);
        }
    }

    private static void verifyColorOnWire(ChatColor color, boolean italic, String expectedName) {
        try {
            UUID playerId = UUID.randomUUID();
            Player target = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getName" -> "Alice";
                        case "getUniqueId" -> playerId;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            PlayerTab tab = new PlayerTab(playerId.toString(), target,
                    new SidebarLine("\u00a7fPrefix "), new SidebarLine("\u00a77 Suffix"),
                    PlayerTab.PushingRule.NEVER, List.of(), color, PlayerTab.NameTagVisibility.ALWAYS);
            tab.setItalic(italic);
            Method render = Sidebar.class.getDeclaredMethod("renderPlayerTab", PlayerTab.class);
            render.setAccessible(true);
            Object rendered = render.invoke(null, tab);
            Method displayName = rendered.getClass().getDeclaredMethod("displayName");
            displayName.setAccessible(true);
            String legacyName = (String) displayName.invoke(rendered);
            if (!legacyName.contains(expectedName)) {
                throw new IllegalStateException("TAB renderer lost team color or italic: " + legacyName);
            }
            var constructor = PlayerListDisplayNamePackets.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object bridge = constructor.newInstance();
            Method create = PlayerListDisplayNamePackets.class.getDeclaredMethod("createDisplayNamePacket", Collection.class);
            create.setAccessible(true);
            Object packet = create.invoke(bridge, List.of(
                    new PlayerListDisplayNameRenderer.RenderedName(target, legacyName)));
            Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf");
            Object rawBuffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
            try {
                Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess");
                Object buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf")
                        .getConstructor(byteBuf, registryAccess)
                        .newInstance(rawBuffer, registryAccess.getField("EMPTY").get(null));
                Object codec = packet.getClass().getField("STREAM_CODEC").get(null);
                Class.forName("net.minecraft.network.codec.StreamEncoder")
                        .getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
                Object decoded = Class.forName("net.minecraft.network.codec.StreamDecoder")
                        .getMethod("decode", Object.class).invoke(codec, buffer);
                Object entry = ((List<?>) decoded.getClass().getMethod("entries").invoke(decoded)).getFirst();
                Object decodedName = entry.getClass().getMethod("displayName").invoke(entry);
                String decodedLegacy = (String) Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage")
                        .getMethod("fromComponent", Class.forName("net.minecraft.network.chat.Component"))
                        .invoke(null, decodedName);
                if (!decodedLegacy.contains(expectedName)
                        || !playerId.equals(entry.getClass().getMethod("profileId").invoke(entry))) {
                    throw new IllegalStateException("Paper PlayerInfo codec lost TAB style: " + decodedLegacy);
                }
            } finally {
                byteBuf.getMethod("release").invoke(rawBuffer);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to verify TAB team color on the Paper wire codec", exception);
        }
    }

    private static void bootstrapMinecraftRegistries() {
        try {
            Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
            Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to bootstrap Paper 1.21.11 runtime", exception);
        }
    }
}
