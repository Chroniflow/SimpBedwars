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

package com.andrei1058.bedwars.configuration;

import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class GeneratorsConfig extends ConfigManager {

    static final int DEFAULT_IRON_DELAY = 1;
    static final int DEFAULT_GOLD_DELAY = 4;

    public GeneratorsConfig(Plugin plugin, String name, String dir) {
        super(plugin, name, dir);

        YamlConfiguration yml = getYml();
        yml.options().header(plugin.getDescription().getName() + "，由 SimpMC 维护。" +
                "\ngenerators.yml 资源生成器配置，适用于 Paper 1.21.11 与 26.2 服务器。\n");
        yml.addDefault("Default." + ConfigPath.GENERATOR_IRON_DELAY, DEFAULT_IRON_DELAY);
        yml.addDefault("Default." + ConfigPath.GENERATOR_IRON_AMOUNT, 2);
        yml.addDefault("Default." + ConfigPath.GENERATOR_GOLD_DELAY, DEFAULT_GOLD_DELAY);
        yml.addDefault("Default." + ConfigPath.GENERATOR_GOLD_AMOUNT, 2);
        yml.addDefault("Default." + ConfigPath.GENERATOR_IRON_SPAWN_LIMIT, 32);
        yml.addDefault("Default." + ConfigPath.GENERATOR_GOLD_SPAWN_LIMIT, 7);
        yml.addDefault(ConfigPath.GENERATOR_STACK_ITEMS, false);

        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_I_DELAY, 30);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_I_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_I_SPAWN_LIMIT, 4);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_II_DELAY, 20);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_II_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_II_SPAWN_LIMIT, 6);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_II_START, 360);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_III_DELAY, 15);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_III_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_III_SPAWN_LIMIT, 8);
        yml.addDefault("Default." + ConfigPath.GENERATOR_DIAMOND_TIER_III_START, 1080);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_I_DELAY, 70);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_I_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_I_SPAWN_LIMIT, 4);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_II_DELAY, 50);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_II_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_II_SPAWN_LIMIT, 6);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_II_START, 720);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_III_DELAY, 30);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_III_AMOUNT, 1);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_III_SPAWN_LIMIT, 8);
        yml.addDefault("Default." + ConfigPath.GENERATOR_EMERALD_TIER_III_START, 1440);
        yml.options().copyDefaults(true);
        setComments("Default", "默认资源生成器参数；delay 和 start 的单位为秒。", "amount 为每次生成数量；spawn-limit 为生成点附近同类地面积压达到该总数后暂停生成，拾取后恢复。");
        setComments(ConfigPath.GENERATOR_STACK_ITEMS, "是否在生成时直接合并同类资源，开启可降低物品实体数量。");
        ChineseConfigDocumentation.generators(this);
        updateToLatestVersion(6, GeneratorsConfig::migrateLegacyDefaults);
    }

    static void migrateLegacyDefaults(YamlConfiguration yml) {
        // The delay migration belongs to schema 4. Later documentation-only
        // upgrades must preserve administrators who deliberately restored 2/6.
        if (yml.getInt(CONFIG_VERSION_PATH, 0) >= 4) return;
        replaceLegacyNumber(yml, "Default." + ConfigPath.GENERATOR_IRON_DELAY, 2, DEFAULT_IRON_DELAY);
        replaceLegacyNumber(yml, "Default." + ConfigPath.GENERATOR_GOLD_DELAY, 6, DEFAULT_GOLD_DELAY);
    }

    private static void replaceLegacyNumber(YamlConfiguration yml, String path, int oldValue, int newValue) {
        Object value = yml.get(path);
        if (value instanceof Number number && Double.compare(number.doubleValue(), oldValue) == 0) {
            yml.set(path, newValue);
        }
    }
}
