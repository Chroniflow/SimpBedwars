/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
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

package com.andrei1058.bedwars.arena.tasks;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.NextEvent;
import com.andrei1058.bedwars.api.arena.generator.GeneratorType;
import com.andrei1058.bedwars.api.arena.generator.IGenerator;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.tasks.StartingTask;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.ArenaStartPolicy;
import com.andrei1058.bedwars.arena.team.BedWarsTeam;
import com.andrei1058.bedwars.configuration.Sounds;
import com.andrei1058.bedwars.support.papi.SupportPAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;

import static com.andrei1058.bedwars.BedWars.nms;
import static com.andrei1058.bedwars.api.language.Language.getList;
import static com.andrei1058.bedwars.api.language.Language.getMsg;

public class GameStartingTask implements Runnable, StartingTask {

    private int countdown;
    private final IArena arena;
    private final BukkitTask task;
    private boolean singleTeamDebugStart;

    public GameStartingTask(Arena arena) {
        this.arena = arena;
        countdown = BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_START_COUNTDOWN_REGULAR);
        task = Bukkit.getScheduler().runTaskTimer(BedWars.plugin, this, 0, 20L);
    }


    /**
     * Get countdown value
     */
    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int countdown) {
        this.countdown = countdown;
    }

    public void enableSingleTeamDebugStart() {
        singleTeamDebugStart = true;
    }

    @Override
    public boolean isSingleTeamDebugStart() {
        return singleTeamDebugStart;
    }

    /**
     * Get arena
     */
    public IArena getArena() {
        return arena;
    }

    /**
     * Get task ID
     */
    public int getTask() {
        return task.getTaskId();
    }

    @Override
    public BukkitTask getBukkitTask() {
        return task;
    }

    @Override
    public void run() {
        if (countdown == 0) {
            getArena().getTeamAssigner().assignTeams(getArena());
            java.util.List<Integer> teamSizes = getArena().getTeams().stream()
                    .map(team -> team.getMembers().size())
                    .toList();
            if (!ArenaStartPolicy.canStartWithTeamSizes(teamSizes,
                    getArena().getMinPlayers(), getArena().getMaxInTeam(), singleTeamDebugStart)) {
                abortInvalidTeamAssignment();
                return;
            }

            // Start every round from empty game-local storage. PlayerGoods
            // keeps the lobby copy separately for restoration on arena exit.
            getArena().getPlayers().forEach(player -> {
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getEnderChest().clear();
            });

            //Color bed block if possible
            //Destroy bed if team is empty
            //Spawn shops and upgrades
            //Disable generators for empty teams if required
            for (ITeam team : getArena().getTeams()) {
                nms.colorBed(team);
                if (team.getMembers().isEmpty()) {
                    team.setBedDestroyed(true);
                    if (getArena().getConfig().getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS)) {
                        for (IGenerator gen : team.getGenerators()) {
                            gen.disable();
                        }
                    }
                }
            }

            Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> {
                //Enable diamond/ emerald generators
                for (IGenerator og : getArena().getOreGenerators()) {
                    if (og.getType() == GeneratorType.EMERALD || og.getType() == GeneratorType.DIAMOND)
                        og.enableRotation();
                }
            }, 60L);

            //Spawn players
            spawnPlayers();

            //Lobby removal
            BedWars.getAPI().getRestoreAdapter().onLobbyRemoval(arena);


            task.cancel();
            getArena().changeStatus(GameState.playing);

            // Check if emerald should be first based on time
            if (getArena().getUpgradeDiamondsCount() < getArena().getUpgradeEmeraldsCount()) {
                getArena().setNextEvent(NextEvent.DIAMOND_GENERATOR_TIER_II);
            } else {
                getArena().setNextEvent(NextEvent.EMERALD_GENERATOR_TIER_II);
            }

            //Spawn shopkeepers
            for (ITeam bwt : getArena().getTeams()) {
                bwt.spawnNPCs();
            }
            return;
        }

        //Send countdown
        if (getCountdown() % 10 == 0 || getCountdown() <= 5) {
            if (getCountdown() < 5) {
                Sounds.playSound(ConfigPath.SOUNDS_COUNTDOWN_TICK_X + getCountdown(), getArena().getPlayers());
            } else {
                Sounds.playSound(ConfigPath.SOUNDS_COUNTDOWN_TICK, getArena().getPlayers());
            }
            for (Player player : getArena().getPlayers()) {
                Language playerLang = Language.getPlayerLanguage(player);
                String[] titleSubtitle = Language.getCountDownTitle(playerLang, getCountdown());
                nms.sendTitle(player, titleSubtitle[0], titleSubtitle[1], 0, 20, 10);
                player.sendMessage(getMsg(player, Messages.ARENA_STATUS_START_COUNTDOWN_CHAT).replace("{time}", String.valueOf(getCountdown())));
            }
        }
        countdown--;
    }

    //Spawn players
    private void spawnPlayers() {
        for (ITeam bwt : getArena().getTeams()) {
            for (Player p : new ArrayList<>(bwt.getMembers())) {
                BedWarsTeam.reSpawnInvulnerability.put(p.getUniqueId(), System.currentTimeMillis() + 2000L);
                bwt.firstSpawn(p);
                Sounds.playSound(ConfigPath.SOUND_GAME_START, p);
                nms.sendTitle(p, getMsg(p, Messages.ARENA_STATUS_START_PLAYER_TITLE), null, 0, 30, 10);
                for (String tut : getList(p, Messages.ARENA_STATUS_START_PLAYER_TUTORIAL)) {
                    p.sendMessage(SupportPAPI.getSupportPAPI().replace(p, tut));
                }
            }
        }
    }

    public void cancel() {
        task.cancel();
    }

    private void abortInvalidTeamAssignment() {
        for (ITeam team : getArena().getTeams()) {
            if (team instanceof BedWarsTeam bedWarsTeam) {
                bedWarsTeam.clearPreGameAssignments();
            } else {
                for (Player player : new ArrayList<>(team.getMembers())) {
                    team.destroyBedHolo(player);
                }
                team.getMembers().clear();
            }
        }
        BedWars.plugin.getLogger().warning("已停止竞技场 " + getArena().getArenaName()
                + " 的开局：分队后少于两支非空队伍，或有队伍人数不在配置的最小/最大范围内。");
        for (Player player : getArena().getPlayers()) {
            player.sendMessage(getMsg(player, Messages.ARENA_START_COUNTDOWN_STOPPED_INSUFF_PLAYERS_CHAT));
        }
        task.cancel();
        getArena().changeStatus(GameState.waiting);
    }
}
