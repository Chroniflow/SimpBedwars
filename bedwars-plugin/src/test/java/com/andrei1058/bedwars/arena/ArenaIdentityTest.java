package com.andrei1058.bedwars.arena;

import com.andrei1058.bedwars.api.arena.IArena;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaIdentityTest {

    @Test
    void usesWorldNameForEqualityAndHashCode() {
        IArena sameWorld = arenaWithWorld("arena-1");
        IArena otherWorld = arenaWithWorld("arena-2");

        assertTrue(ArenaIdentity.equals("arena-1", sameWorld));
        assertFalse(ArenaIdentity.equals("arena-1", otherWorld));
        assertFalse(ArenaIdentity.equals("arena-1", null));
        assertEquals(ArenaIdentity.hashCode(sameWorld.getWorldName()),
                ArenaIdentity.hashCode("arena-1"));
    }

    private static IArena arenaWithWorld(String worldName) {
        return (IArena) Proxy.newProxyInstance(
                ArenaIdentityTest.class.getClassLoader(),
                new Class<?>[]{IArena.class},
                (proxy, method, args) -> method.getName().equals("getWorldName") ? worldName : null
        );
    }
}
