/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.andrei1058.bedwars.arena;

import com.andrei1058.bedwars.api.arena.IArena;

import java.util.Objects;

final class ArenaIdentity {

    private ArenaIdentity() {
    }

    static boolean equals(String worldName, Object other) {
        return other instanceof IArena arena && Objects.equals(worldName, arena.getWorldName());
    }

    static int hashCode(String worldName) {
        return Objects.hashCode(worldName);
    }
}
