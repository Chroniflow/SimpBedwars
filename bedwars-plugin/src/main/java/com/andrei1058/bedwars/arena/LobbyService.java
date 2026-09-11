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

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.listeners.LobbyAnnouncements;
import com.andrei1058.bedwars.support.papi.SupportPAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.andrei1058.bedwars.api.language.Language.getList;
import static com.andrei1058.bedwars.api.language.Language.getMsg;

/**
 * Owns the main-lobby player state and command-item lifecycle.
 *
 * <p>The static facade in {@link Arena} remains for compatibility, while the
 * implementation lives here so arena lifecycle code does not also own lobby
 * inventory parsing, conflict resolution and delayed reconciliation.</p>
 */
final class LobbyService {

    private static final Set<String> WARNED_ITEM_PROBLEMS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> ITEM_RECHECK_GENERATIONS = new ConcurrentHashMap<>();

    private LobbyService() {
    }

    static void enter(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BedWars.plugin, () -> enter(player));
            return;
        }
        if (!isCurrentLobbyPlayer(player)) return;
        player.setGameMode(GameMode.ADVENTURE);
        PlayerMotion.disableFlight(player);
        player.setCanPickupItems(true);
        // Arena departure may have restored the saved inventory. The lobby
        // owns a separate loadout, so rebuild every player-inventory slot.
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(new ItemStack(Material.AIR));
        refreshCommandItems(player);
        scheduleItemRecheck(player);
        LobbyAnnouncements.playerEntered(player);
    }

    static void sendCommandItems(Player player) {
        // Compatibility API: callers historically rely on this delayed clear.
        if (!BedWars.config.getYml().isConfigurationSection(
                ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH)) return;
        Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> {
            if (!isCurrentLobbyPlayer(player)) return;
            player.getInventory().clear();
            refreshCommandItems(player);
        }, 15L);
    }

    static void refreshCommandItems(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BedWars.plugin, () -> refreshCommandItems(player));
            return;
        }
        if (!isCurrentLobbyPlayer(player)) return;

        removeCommandItems(player);
        List<LobbyCommandItem> configuredItems = readCommandItems();
        Map<String, LobbyCommandItem> byId = new LinkedHashMap<>();
        configuredItems.forEach(item -> byId.put(item.id(), item));
        LobbyItemLayout.Result layout = LobbyItemLayout.resolve(configuredItems.stream()
                .map(item -> new LobbyItemLayout.Item(item.id(), item.slot(), item.returnPriority()))
                .toList());
        warnConflicts(layout, byId);
        applyItems(player, layout, byId);
    }

    static boolean isCurrentLobbyPlayer(Player player) {
        String playerWorld = player.getWorld() == null ? null : player.getWorld().getName();
        return LobbyInventoryPolicy.shouldApply(player.isOnline(), Arena.isInArena(player),
                SetupSession.isInSetupSession(player.getUniqueId()), playerWorld,
                BedWars.config.getLobbyWorldName());
    }

    private static void scheduleItemRecheck(Player player) {
        UUID playerId = player.getUniqueId();
        int generation = ITEM_RECHECK_GENERATIONS.merge(playerId, 1, Integer::sum);
        Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> {
            if (!ITEM_RECHECK_GENERATIONS.remove(playerId, generation)) return;
            refreshCommandItems(player);
        }, 15L);
    }

    private static void warnConflicts(LobbyItemLayout.Result layout, Map<String, LobbyCommandItem> byId) {
        for (LobbyItemLayout.Conflict conflict : layout.conflicts()) {
            LobbyCommandItem selected = byId.get(conflict.selectedId());
            String resolution = selected != null && selected.returnPriority() > 0
                    ? "为保证返回代理大厅物品可用"
                    : "按配置中的既有后写顺序";
            warnItemProblem("slot:" + conflict.slot() + ':' + conflict.replacedId() + ':' + conflict.selectedId(),
                    "大厅物品槽位 " + (conflict.slot() + 1) + " 同时配置了 " + conflict.replacedId()
                            + " 和 " + conflict.selectedId() + "；" + resolution + "，使用 "
                            + conflict.selectedId() + "。请修改 config.yml 消除冲突。");
        }
    }

    private static void applyItems(Player player, LobbyItemLayout.Result layout,
                                   Map<String, LobbyCommandItem> byId) {
        for (LobbyItemLayout.Item selected : layout.items()) {
            LobbyCommandItem item = byId.get(selected.id());
            if (item == null) continue;
            try {
                ItemStack stack = Misc.createItem(item.material(), item.data(), item.enchanted(),
                        SupportPAPI.getSupportPAPI().replace(player,
                                getMsg(player, Messages.GENERAL_CONFIGURATION_LOBBY_ITEMS_NAME
                                        .replace("%path%", item.id()))),
                        SupportPAPI.getSupportPAPI().replace(player,
                                getList(player, Messages.GENERAL_CONFIGURATION_LOBBY_ITEMS_LORE
                                        .replace("%path%", item.id()))),
                        player, "RUNCOMMAND", item.command());
                stack = CommandItemAction.tagReturnItem(stack, item.id(), item.command(),
                        BedWars.mainCmd, CommandItemAction.Target.PROXY_LOBBY);
                player.getInventory().setItem(item.slot(), stack);
            } catch (RuntimeException exception) {
                warnItemProblem("build:" + item.id(), "无法创建大厅物品 " + item.id()
                        + "，已跳过该物品：" + exception.getMessage());
            }
        }
    }

    private static void removeCommandItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (CommandItemAction.isCommandItem(item)) player.getInventory().setItem(slot, null);
        }
    }

    private static List<LobbyCommandItem> readCommandItems() {
        if (!BedWars.config.getYml().isConfigurationSection(
                ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH)) return List.of();

        List<LobbyCommandItem> items = new ArrayList<>();
        for (String id : Objects.requireNonNull(BedWars.config.getYml().getConfigurationSection(
                ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH)).getKeys(false)) {
            String base = ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH + '.' + id;
            String missing = List.of("material", "data", "slot", "enchanted", "command").stream()
                    .filter(field -> !BedWars.config.getYml().isSet(base + '.' + field))
                    .findFirst().orElse(null);
            if (missing != null) {
                warnItemProblem("missing:" + id + ':' + missing,
                        "大厅物品 " + id + " 缺少配置 " + base + '.' + missing + "，已跳过。");
                continue;
            }

            Material material;
            try {
                material = Material.valueOf(BedWars.config.getYml().getString(base + ".material", "")
                        .trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                warnItemProblem("material:" + id, "大厅物品 " + id + " 的材质无效，已跳过："
                        + BedWars.config.getYml().getString(base + ".material"));
                continue;
            }
            int slot = BedWars.config.getYml().getInt(base + ".slot");
            if (slot < 0 || slot >= 41) {
                warnItemProblem("slot-range:" + id, "大厅物品 " + id
                        + " 的槽位必须在 0 到 40 之间，当前为 " + slot + "，已跳过。");
                continue;
            }
            String command = BedWars.config.getYml().getString(base + ".command", "");
            items.add(new LobbyCommandItem(id, material,
                    (byte) BedWars.config.getYml().getInt(base + ".data"),
                    BedWars.config.getYml().getBoolean(base + ".enchanted"), slot, command,
                    CommandItemAction.returnItemPriority(id, command, BedWars.mainCmd)));
        }
        return items;
    }

    private static void warnItemProblem(String key, String message) {
        if (WARNED_ITEM_PROBLEMS.add(key)) BedWars.plugin.getLogger().warning(message);
    }

    private record LobbyCommandItem(String id, Material material, byte data, boolean enchanted,
                                    int slot, String command, int returnPriority) {
    }
}
