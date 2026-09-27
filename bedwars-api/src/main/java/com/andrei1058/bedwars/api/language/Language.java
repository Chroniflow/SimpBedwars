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

package com.andrei1058.bedwars.api.language;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.events.player.PlayerLangChangeEvent;
import com.andrei1058.bedwars.api.util.AdventureText;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Language extends ConfigManager {

    /** The only player-facing language bundled by SimpMC-BedWars. */
    public static final String SIMPLIFIED_CHINESE_ISO = "zh_cn";

    private static final Pattern LANGUAGE_FILE = Pattern.compile("^messages_([A-Za-z0-9_-]+)\\.yml$");

    private final String iso;
    private String prefix = "";
    private static String prefixStatic = "";
    private static final HashMap<UUID, Language> langByPlayer = new HashMap<>();
    private static final List<Language> languages = new ArrayList<>();
    private static Language defaultLanguage;
    private String serverIp;

    public Language(Plugin plugin, String iso) {
        super(plugin, "messages_" + iso, plugin.getDataFolder().getPath() + "/Languages");
        migrateLegacyUpgradeMessageKeys(getYml());
        this.iso = iso;
        migrateLegacyTowerShopItem(getYml());
        addContentMessages(getYml(), "tower", ConfigPath.SHOP_PATH_CATEGORY_UTILITY,
                "{color}Compact Pop-up Tower",
                List.of("&7Cost: {cost} {currency}", "", "&7Place a compact pop-up",
                        "&7tower defense!", "", "{quick_buy}", "{buy_status}"));
        addContentMessages(getYml(), "recall-scroll", ConfigPath.SHOP_PATH_CATEGORY_UTILITY,
                "{color}Recall Scroll",
                List.of("&7Cost: {cost} {currency}", "", "&7Use to return to your team",
                        "&7spawn after a 5-second channel.", "", "{quick_buy}", "{buy_status}"));
        getYml().addDefault(Messages.RECALL_SCROLL_COUNTDOWN,
                "&bReturning to base in &f{time}&b seconds...");
        getYml().addDefault(Messages.RECALL_SCROLL_ALREADY_CHANNELING,
                "&cA recall scroll is already channeling.");
        getYml().addDefault(Messages.RECALL_SCROLL_UNAVAILABLE,
                "&cYou cannot use a recall scroll right now.");
        getYml().addDefault(Messages.RECALL_SCROLL_CANCELLED,
                "&cRecall cancelled; the scroll was returned.");
        getYml().addDefault(Messages.RECALL_SCROLL_COMPLETED,
                "&aReturned to your team spawn.");
        getYml().addDefault(Messages.RECALL_SCROLL_FAILED,
                "&cRecall teleport failed.");
        getYml().addDefault(Messages.ARENA_RESTART_COUNTDOWN,
                "&eArena resets in &c{time}&e seconds.");
        getYml().addDefault(Messages.FORMATTING_SB_TAB_GAME_TIME,
                "&7Game time: &a{gameTime}");
        getYml().addDefault(Messages.FORMATTING_SB_TAB_GAME_ID,
                "&7Game ID: &f{gameId}");

        // replace old placeholders
        List<String> oldMsg = getYml().getStringList(Messages.GAME_END_TOP_PLAYER_CHAT);
        if (!oldMsg.isEmpty()) {
            String[] oldTop1 = new String[]{"{firstName}", "{secondName}", "{thirdName}"};
            String[] oldTop2 = new String[]{"{firstKills}", "{secondKills}", "{thirdKills}"};

            List<String> newMsg = new ArrayList<>();
            for (String oldString : oldMsg) {
                for (String oldPlaceholder : oldTop1) {
                    oldString = oldString.replace(oldPlaceholder, "{topPlayerName}");
                }
                for (String oldPlaceholder : oldTop2) {
                    oldString = oldString.replace(oldPlaceholder, "{topValue}");
                }
                newMsg.add(oldString);
            }

            getYml().set(Messages.GAME_END_TOP_PLAYER_CHAT, newMsg);
        }

        if (null != getYml().get("scoreboard")) {
            for (String group : getYml().getConfigurationSection("scoreboard").getKeys(false)) {
                if (group.equalsIgnoreCase("lobby")) {
                    relocate("scoreboard." + group, "sidebar." + group);
                } else {
                    Map<String, String[]> stages = new HashMap<>();
                    stages.put("waiting", new String[]{Messages.SCOREBOARD_DEFAULT_WAITING, Messages.SCOREBOARD_DEFAULT_WAITING_SPEC});
                    stages.put("starting", new String[]{Messages.SCOREBOARD_DEFAULT_STARTING, Messages.SCOREBOARD_DEFAULT_STARTING_SPEC});

                    for (Map.Entry<String, String[]> stage : stages.entrySet()) {
                        if (exists("scoreboard." + group + "." + stage.getKey() + ".player")) {
                            relocate("scoreboard." + group + "." + stage.getKey() + ".player", stage.getValue()[0].replace("Default", group));
                        } else {
                            relocate("scoreboard." + group + "." + stage.getKey(), stage.getValue()[0].replace("Default", group));
                        }
                        if (exists("scoreboard." + group + "." + stage.getKey() + ".spectator")) {
                            relocate("scoreboard." + group + "." + stage.getKey() + ".spectator", stage.getValue()[1].replace("Default", group));
                        }
                    }
                    if (exists("scoreboard." + group + ".playing.alive")) {
                        relocate("scoreboard." + group + ".playing.alive", Messages.SCOREBOARD_DEFAULT_PLAYING.replace("Default", group));
                        relocate("scoreboard." + group + ".playing.spectator", Messages.SCOREBOARD_DEFAULT_PLAYING.replace("Default", group));
                    } else {
                        relocate("scoreboard." + group + ".playing", Messages.SCOREBOARD_DEFAULT_PLAYING.replace("Default", group));
                    }
                }
            }
            getYml().set("scoreboard", null);
        }

        // API-created language instances do not have a later defaults phase,
        // so finish their migration here. The plugin itself only registers
        // SimplifiedChinese.
        addChineseDocumentation();
        if (getClass() == Language.class) {
            updateToLatestVersion(5, Language::migrateLegacyTowerShopItem);
            save();
        }
        languages.add(this);
    }

    /**
     * Add Chinese documentation shared by built-in and custom language files.
     */
    public void addChineseDocumentation() {
        String documentation = "SimpMC-BedWars 语言文件。\n"
                + "本文件只控制玩家可见文本；颜色使用 & 代码，占位符（例如 {player}）不得删除。";
        String currentHeader = getYml().options().header();
        if (currentHeader == null || currentHeader.isBlank()) {
            getYml().options().header(documentation);
        } else if (!currentHeader.contains("SimpMC-BedWars 语言文件")) {
            getYml().options().header(documentation + "\n" + currentHeader);
        }
        setComments("name", "语言在 /bw lang 菜单中的显示名称。");
        setComments(Messages.PREFIX, "插件消息前缀；留空表示不添加统一前缀。");
        setComments(Messages.COMMAND_MAIN, "玩家执行 /bw 时看到的命令帮助文本。");
        setComments(Messages.FORMATTING_CHAT_LOBBY, "大厅聊天格式；必须保留 {player} 和 {message}。", "运行时会统一使用 &f> &7 分隔名字和消息。");
        setComments(Messages.FORMATTING_CHAT_WAITING, "竞技场等待阶段聊天格式。");
        setComments(Messages.FORMATTING_CHAT_SHOUT, "游戏内全体喊话格式。");
        setComments(Messages.FORMATTING_CHAT_TEAM, "游戏内队伍聊天格式。");
        setComments(Messages.FORMATTING_CHAT_SPECTATOR, "观战者聊天格式。");
        setComments(Messages.ARENA_RESTART_COUNTDOWN, "游戏结束后的竞技场重置倒计时；必须保留 {time}。");
        setComments(Messages.FORMATTING_SB_TAB_BASE,
                "TAB 玩家列表的头部、底部、玩家名前缀与后缀；大厅和竞技场全部状态至少保持 128 空格宽度。");
        setComments(Messages.FORMATTING_SB_TAB_GAME_TIME,
                "游戏进行时插入 TAB 页首的本局已进行时间；必须保留 {gameTime}，留空可隐藏该行。");
        setComments(Messages.FORMATTING_SB_TAB_GAME_ID,
                "竞技场 TAB 页首显示本局对局编号；必须保留 {gameId}，留空可隐藏该行。");
        setComments(Messages.SCOREBOARD_LOBBY, "主大厅计分板文本列表。");
        setComments(Messages.SCOREBOARD_DEFAULT_WAITING, "默认分组等待阶段计分板文本列表。");
        setComments(Messages.SCOREBOARD_DEFAULT_PLAYING, "默认分组游戏阶段计分板文本列表。");
    }

    /**
     * Return the ISO portion of a canonical language filename. This generic
     * parser remains for legacy API callers; the plugin runtime only loads
     * {@link #SIMPLIFIED_CHINESE_ISO}. Migration backups such as
     * messages_zh_cn.yml.v1.bak are intentionally rejected.
     */
    public static Optional<String> isoFromFileName(String fileName) {
        if (fileName == null) return Optional.empty();
        Matcher matcher = LANGUAGE_FILE.matcher(fileName);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Set chat prefix.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setPrefixStatic(String prefix) {
        prefixStatic = prefix;
    }

    /**
     * Get scoreboard strings.
     */
    public static List<String> getScoreboard(Player player, String path, String alternative) {
        Language language = getPlayerLanguage(player);
        if (language.exists(path)) {
            return language.l(path);
        } else {
            if (path.split("\\.").length == 3) {
                String[] sp = path.split("\\.");
                String path2 = sp[1];
                path2 = String.valueOf(path2.charAt(0)).toUpperCase() + path2.substring(1).toLowerCase();
                path2 = sp[0] + "." + path2 + "." + sp[2];
                if (language.exists(path2)) {
                    return language.l(path);
                } else if (language.exists(sp[0] + "." + sp[1].toUpperCase() + "." + sp[2])) {
                    return language.l(sp[0] + "." + sp[1].toUpperCase() + "." + sp[2]);
                }
            }
        }
        return language.l(alternative);
    }

    /**
     * Get language display name.
     */
    public String getLangName() {
        return getYml().getString("name");
    }

    /**
     * Get message in player's language.
     */
    public static String getMsg(Player player, String path) {
        if (player == null) {
            return getDefaultLanguage().m(path);
        }
        return langByPlayer.getOrDefault(player.getUniqueId(), getDefaultLanguage())
                .m(path).replace("{prefix}", (prefixStatic == null ? "" : prefixStatic));
    }

    /**
     * Retrieve a player language.
     */
    public static Language getPlayerLanguage(@NotNull Player player) {
        return langByPlayer.getOrDefault(player.getUniqueId(), getDefaultLanguage());
    }

    public static Language getPlayerLanguage(UUID p) {
        return langByPlayer.getOrDefault(p, getDefaultLanguage());
    }

    /**
     * Check if a message was set.
     */
    public boolean exists(String path) {
        return getYml().get(resolveMessagePath(path)) != null;
    }

    /**
     * Get a string list in player's language.
     */
    public static List<String> getList(@NotNull Player player, String path) {
        return langByPlayer.getOrDefault(player.getUniqueId(), getDefaultLanguage()).l(path);
    }

    @SuppressWarnings("unused")
    public void relocate(String from, String to) {
        Object fromData = getYml().get(from);
        if (null != fromData) {
            this.getYml().set(to, fromData);
            this.getYml().set(from, null);
        }
    }

    /**
     * Save a value to file if not exists.
     */
    public static void saveIfNotExists(String path, Object data) {
        for (Language l : languages) {
            if (l.getYml().get(path) == null) {
                l.set(path, data);
            }
        }
    }

    /**
     * Get a color translated message.
     */
    public String m(String path) {
        String resolvedPath = resolveMessagePath(path);
        String message = getYml().getString(resolvedPath);
        if (message == null) {
            plugin.getLogger().warning("Missing message key " + path + " in language " + getIso());
            message = "MISSING_LANG";
        }
        if (null == serverIp) {
            BedWars api = Bukkit.getServicesManager().getRegistration(BedWars.class).getProvider();
            if (null != api.getConfigs().getMainConfig()) {
                serverIp = api.getConfigs().getMainConfig().
                        getString(ConfigPath.GENERAL_CONFIG_PLACEHOLDERS_REPLACEMENTS_SERVER_IP);
            }
        }

        return AdventureText.section(AdventureText.ampersand(message
                .replace("{prefix}", (prefix == null ? "" : prefix))
                .replace("{serverIp}", serverIp == null ? "" : serverIp)
        ));
    }

    /**
     * Get a color translated list.
     */
    public List<String> l(String path) {
        List<String> result = new ArrayList<>();
        String resolvedPath = resolveMessagePath(path);
        List<String> lines;
        if (!getYml().isList(resolvedPath)) {
            plugin.getLogger().warning("Missing message list key " + path + " in language " + getIso());
            lines = Collections.emptyList();
        } else {
            lines = getYml().getStringList(resolvedPath);
        }
        for (String line : lines) {
            result.add(AdventureText.section(AdventureText.ampersand(line)));
        }
        return result;
    }

    /**
     * Resolve message paths renamed before the Paper-only language schema.
     * Existing administrator files are migrated in the constructor; this
     * lookup remains a harmless guard for a file changed while the server is
     * running or a caller still using the historical public key.
     */
    private String resolveMessagePath(String path) {
        if (path == null || getYml().get(path) != null) return path;
        if (path.startsWith("upgrades-name-")) {
            String modern = "upgrades-upgrade-name-" + path.substring("upgrades-name-".length());
            if (getYml().get(modern) != null) return modern;
        } else if (path.startsWith("upgrades-upgrade-name-")) {
            String legacy = "upgrades-name-" + path.substring("upgrades-upgrade-name-".length());
            if (getYml().get(legacy) != null) return legacy;
        }
        if (path.startsWith("upgrades-lore-")) {
            String modern = "upgrades-upgrade-lore-" + path.substring("upgrades-lore-".length());
            if (getYml().get(modern) != null) return modern;
        } else if (path.startsWith("upgrades-upgrade-lore-")) {
            String legacy = "upgrades-lore-" + path.substring("upgrades-upgrade-lore-".length());
            if (getYml().get(legacy) != null) return legacy;
        }
        return path;
    }

    static void migrateLegacyUpgradeMessageKeys(@NotNull YamlConfiguration yml) {
        for (String path : new ArrayList<>(yml.getKeys(true))) {
            String modern;
            if (path.startsWith("upgrades-name-")) {
                modern = "upgrades-upgrade-name-" + path.substring("upgrades-name-".length());
            } else if (path.startsWith("upgrades-lore-")) {
                modern = "upgrades-upgrade-lore-" + path.substring("upgrades-lore-".length());
            } else {
                continue;
            }
            if (yml.get(modern) == null) yml.set(modern, yml.get(path));
        }
    }

    public static HashMap<UUID, Language> getLangByPlayer() {
        return langByPlayer;
    }

    /**
     * Check if a language exists.
     */
    public static boolean isLanguageExist(String iso) {
        if (!isSimplifiedChineseIso(iso)) return false;
        for (Language l : languages) {
            if (l.iso.equalsIgnoreCase(iso)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether an ISO code is supported by the bundled player language.
     * Custom {@link Language} instances remain constructible for API
     * compatibility, while normal plugin commands expose only this code.
     */
    public static boolean isSimplifiedChineseIso(String iso) {
        return SIMPLIFIED_CHINESE_ISO.equalsIgnoreCase(normalizeIso(iso));
    }

    /** Normalize ISO values coming from legacy databases and proxy payloads. */
    private static String normalizeIso(String iso) {
        return iso == null ? null : iso.trim();
    }

    /**
     * Get language with given info.
     *
     * @return the configured default language when the ISO code is unknown.
     */
    public static Language getLang(String iso) {
        iso = normalizeIso(iso);
        if (!isSimplifiedChineseIso(iso)) return getDefaultLanguage();
        for (Language l : languages) {
            if (l.iso.equalsIgnoreCase(iso)) {
                return l;
            }
        }
        return getDefaultLanguage();
    }

    /**
     * Get language iso code.
     */
    public String getIso() {
        return iso;
    }

    /**
     * Get loaded languages list.
     */
    public static List<Language> getLanguages() {
        return languages;
    }

    /**
     * Save messages for unset stats items.
     */
    public static void setupCustomStatsMessages() {
        BedWars api = Bukkit.getServer().getServicesManager().getRegistration(BedWars.class).getProvider();
        for (Language l : getLanguages()) {
            if (l == null) continue;
            if (l.getYml() == null) continue;
            /* save messages for stats gui items if custom items added */
            if (api.getConfigs().getMainConfig().getYml().get(ConfigPath.GENERAL_CONFIGURATION_STATS_PATH) == null)
                return;
            for (String item : api.getConfigs().getMainConfig().getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_STATS_PATH).getKeys(false)) {
                if (ConfigPath.GENERAL_CONFIGURATION_STATS_GUI_SIZE.contains(item)) continue;
                if (l.getYml().getDefaults() == null || !l.getYml().getDefaults().contains(Messages.PLAYER_STATS_GUI_PATH + "-" + item + "-name"))
                    l.getYml().addDefault(Messages.PLAYER_STATS_GUI_PATH + "-" + item + "-name", "Name not set");
                if (l.getYml().getDefaults() == null || !l.getYml().getDefaults().contains(Messages.PLAYER_STATS_GUI_PATH + "-" + item + "-lore"))
                    l.getYml().addDefault(Messages.PLAYER_STATS_GUI_PATH + "-" + item + "-lore", Collections.singletonList("lore not set"));
            }
            l.save();
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void addDefaultStatsMsg(@NotNull YamlConfiguration yml, String path, String name, String... lore) {
        if (yml.getDefaults() == null || !yml.getDefaults().contains(Messages.PLAYER_STATS_GUI_PATH + "-" + path + "-name"))
            yml.addDefault(Messages.PLAYER_STATS_GUI_PATH + "-" + path + "-name", name);
        if (yml.getDefaults() == null || !yml.getDefaults().contains(Messages.PLAYER_STATS_GUI_PATH + "-" + path + "-lore"))
            yml.addDefault(Messages.PLAYER_STATS_GUI_PATH + "-" + path + "-lore", lore);
    }

    /**
     * Create missing name/ lore for items: multi arena lobby, waiting, spectating
     */
    public static void addDefaultMessagesCommandItems(Language language) {
        if (language == null) return;
        YamlConfiguration yml = language.getYml();
        if (yml == null) return;
        BedWars api = Bukkit.getServer().getServicesManager().getRegistration(BedWars.class).getProvider();
        if (api.getConfigs().getMainConfig().getYml().get(ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH) != null) {
            for (String item : api.getConfigs().getMainConfig().getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_LOBBY_ITEMS_PATH).getKeys(false)) {
                if (item.isEmpty()) continue;
                String p1 = Messages.GENERAL_CONFIGURATION_LOBBY_ITEMS_NAME.replace("%path%", item);
                String p2 = Messages.GENERAL_CONFIGURATION_LOBBY_ITEMS_LORE.replace("%path%", item);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p1))
                    yml.addDefault(p1, "&cName not set at: &f" + p1);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p2))
                    yml.addDefault(p2, Arrays.asList("&cLore not set at:", " &f" + p2));
            }
        }
        if (api.getConfigs().getMainConfig().getYml().get(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_PATH) != null) {
            for (String item : api.getConfigs().getMainConfig().getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_PATH).getKeys(false)) {
                if (item.isEmpty()) continue;
                String p1 = Messages.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_NAME.replace("%path%", item);
                String p2 = Messages.GENERAL_CONFIGURATION_SPECTATOR_ITEMS_LORE.replace("%path%", item);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p1))
                    yml.addDefault(p1, "&cName not set at: &f" + p1);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p2))
                    yml.addDefault(p2, Arrays.asList("&cLore not set at:", " &f" + p2));
            }
        }
        if (api.getConfigs().getMainConfig().getYml().get(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_PATH) != null) {
            for (String item : api.getConfigs().getMainConfig().getYml().getConfigurationSection(ConfigPath.GENERAL_CONFIGURATION_PRE_GAME_ITEMS_PATH).getKeys(false)) {
                if (item.isEmpty()) continue;
                String p1 = Messages.GENERAL_CONFIGURATION_WAITING_ITEMS_NAME.replace("%path%", item);
                String p2 = Messages.GENERAL_CONFIGURATION_WAITING_ITEMS_LORE.replace("%path%", item);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p1))
                    yml.addDefault(p1, "&cName not set at: &f" + p1);
                if (yml.getDefaults() == null || !yml.getDefaults().contains(p2))
                    yml.addDefault(p2, Arrays.asList("&cLore not set at:", " &f" + p2));
            }
        }
        yml.options().copyDefaults(true);
        language.save();
    }

    /**
     * Create messages paths for new shop categories
     */
    @SuppressWarnings("DuplicateExpressions")
    public void setupUnSetCategories() {
        BedWars api = Bukkit.getServer().getServicesManager().getRegistration(BedWars.class).getProvider();
        for (String s : api.getConfigs().getShopConfig().getYml().getConfigurationSection("").getKeys(false)) {
            if (s.equalsIgnoreCase(ConfigPath.SHOP_SETTINGS_PATH)) continue;
            if (s.equalsIgnoreCase(ConfigPath.SHOP_SPECIALS_PATH)) continue;
            if (s.equals(ConfigPath.SHOP_QUICK_DEFAULTS_PATH)) continue;
            if (!exists(Messages.SHOP_CATEGORY_INVENTORY_NAME.replace("%category%", s))) {
                set(Messages.SHOP_CATEGORY_INVENTORY_NAME.replace("%category%", s), "&8Name not set");
            }
            if (!exists(Messages.SHOP_CATEGORY_ITEM_NAME.replace("%category%", s))) {
                set(Messages.SHOP_CATEGORY_ITEM_NAME.replace("%category%", s), "&8Name not set");
            }
            if (!exists(Messages.SHOP_CATEGORY_ITEM_LORE.replace("%category%", s))) {
                set(Messages.SHOP_CATEGORY_ITEM_LORE.replace("%category%", s), Collections.singletonList("&8Lore not set"));
            }
            if (api.getConfigs().getShopConfig().getYml().get(s + ConfigPath.SHOP_CATEGORY_CONTENT_PATH) == null)
                continue;
            for (String c : api.getConfigs().getShopConfig().getYml().getConfigurationSection(s + ConfigPath.SHOP_CATEGORY_CONTENT_PATH).getKeys(false)) {
                if (!exists(Messages.SHOP_CONTENT_TIER_ITEM_NAME.replace("%category%", s).replace("%content%", c))) {
                    set(Messages.SHOP_CONTENT_TIER_ITEM_NAME.replace("%category%", s).replace("%content%", c), "&8Name not set");
                }
                if (!exists(Messages.SHOP_CONTENT_TIER_ITEM_LORE.replace("%category%", s).replace("%content%", c))) {
                    set(Messages.SHOP_CONTENT_TIER_ITEM_LORE.replace("%category%", s).replace("%content%", c), Collections.singletonList("&8Lore not set"));
                }
            }
        }
    }

    /**
     * Add required messages for a shop category to the given yml
     */
    public static void addCategoryMessages(@NotNull YamlConfiguration yml, String categoryName, String invName, String itemName, List<String> itemLore) {
        if (yml.getDefaults() == null || !yml.getDefaults().contains(Messages.SHOP_CATEGORY_INVENTORY_NAME.replace("%category%", categoryName)))
            yml.addDefault(Messages.SHOP_CATEGORY_INVENTORY_NAME.replace("%category%", categoryName), invName);
        if (yml.getDefaults() == null || !yml.getDefaults().contains(Messages.SHOP_CATEGORY_ITEM_NAME.replace("%category%", categoryName)))
            yml.addDefault(Messages.SHOP_CATEGORY_ITEM_NAME.replace("%category%", categoryName), itemName);
        if (yml.getDefaults() == null || !yml.getDefaults().contains(Messages.SHOP_CATEGORY_ITEM_LORE.replace("%category%", categoryName)))
            yml.addDefault(Messages.SHOP_CATEGORY_ITEM_LORE.replace("%category%", categoryName), itemLore);
    }

    /**
     * Add required messages for a shop category to the given yml
     */
    public static void addContentMessages(@NotNull YamlConfiguration yml, String contentName, String categoryName, String itemName, List<String> itemLore) {
        final String path1 = Messages.SHOP_CONTENT_TIER_ITEM_NAME.replace("%category%", categoryName).replace("%content%", contentName),
                path2 = Messages.SHOP_CONTENT_TIER_ITEM_LORE.replace("%category%", categoryName).replace("%content%", contentName);
        if (yml.getDefaults() == null || !yml.getDefaults().contains(path1)) yml.addDefault(path1, itemName);
        if (yml.getDefaults() == null || !yml.getDefaults().contains(path2)) yml.addDefault(path2, itemLore);
    }

    /**
     * Add the four built-in sharpness upgrade labels and migrate an unchanged
     * one-tier lore template. Exact-value matching keeps administrator wording
     * intact while fresh and still-default language files describe every tier.
     */
    public static void addSharpnessUpgradeMessages(@NotNull YamlConfiguration yml,
                                                   @NotNull String baseName,
                                                   @NotNull List<String> legacyLore,
                                                   @NotNull List<String> expandedLore) {
        String namePath = Messages.UPGRADES_UPGRADE_TIER_ITEM_NAME.replace("{name}", "swords");
        String[] numerals = {"I", "II", "III", "IV"};
        for (int tier = 1; tier <= numerals.length; tier++) {
            yml.addDefault(namePath.replace("{tier}", "tier-" + tier), baseName + " " + numerals[tier - 1]);
        }

        String lorePath = Messages.UPGRADES_UPGRADE_TIER_ITEM_LORE.replace("{name}", "swords");
        yml.addDefault(lorePath, expandedLore);
        if (yml.getStringList(lorePath).equals(legacyLore)) {
            yml.set(lorePath, expandedLore);
        }

        String tierOnePath = namePath.replace("{tier}", "tier-1");
        if (baseName.equals(yml.getString(tierOnePath))) {
            yml.set(tierOnePath, baseName + " I");
        }
    }

    /**
     * Move the historical pop-up tower translation from its display name to
     * the stable shop content id used by shop.yml. Generated placeholders are
     * discarded so corrected defaults can fill them, while explicit values at
     * the correct path always win.
     */
    public static void migrateLegacyTowerShopItem(@NotNull YamlConfiguration yml) {
        migrateShopContentKey(yml, ConfigPath.SHOP_PATH_CATEGORY_UTILITY,
                "Compact Pop-up Tower", "tower");
    }

    /**
     * Migrate only the player-row values shipped by the plugin itself. An
     * administrator's custom TAB prefix or suffix is deliberately preserved.
     */
    public static void migrateBuiltInTabPlayerRows(@NotNull YamlConfiguration yml) {
        migrateLegacyTowerShopItem(yml);
        List<String> visibleTeamPrefix = List.of("{teamColor}{teamName} ");
        List<String> bracketedTeamPrefix = List.of("&7[{teamColor}{teamName}&7] ");
        replaceKnownList(yml, Messages.FORMATTING_SB_TAB_PLAYING_PREFIX, bracketedTeamPrefix,
                List.of(List.of(""), visibleTeamPrefix, List.of("{teamColor}[{teamLetter}] ")));
        for (String path : List.of(
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX)) {
            replaceKnownList(yml, path, bracketedTeamPrefix, List.of(
                    List.of(""),
                    visibleTeamPrefix,
                    List.of("&6&l⭐ {teamColor}{teamName} "),
                    List.of("&6★ {teamColor}[{teamLetter}] "),
                    List.of("{teamColor}[{teamLetter}] ")
            ));
        }
        replaceKnownList(yml, Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX, bracketedTeamPrefix, List.of(
                List.of(""),
                visibleTeamPrefix,
                List.of("{teamColor}[{teamLetter}] ")
        ));
        replaceKnownList(yml, Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX,
                List.of(" &c&oEliminated", " {teamColor}&oEliminated {vPrefix}",
                        "{teamColor}&oEliminated {level}"),
                List.of(List.of(" &c&oEliminated {teamColor}&o{teamName}",
                        " {teamColor}&oEliminated {vPrefix}",
                        "{teamColor}&oEliminated {level}")));
        replaceKnownList(yml, Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX,
                List.of(""),
                List.of(List.of(" &c&o已淘汰 {teamColor}&o{teamName}",
                        " {teamColor}&o已淘汰 {vPrefix}",
                        "{teamColor}&o已淘汰 {level}")));
    }

    private static void replaceKnownList(YamlConfiguration yml, String path, List<String> replacement,
                                         List<List<String>> knownValues) {
        if (knownValues.contains(yml.getStringList(path))) yml.set(path, replacement);
    }

    static void migrateShopContentKey(YamlConfiguration yml, String category, String oldContent, String newContent) {
        String oldName = Messages.SHOP_CONTENT_TIER_ITEM_NAME
                .replace("%category%", category).replace("%content%", oldContent);
        String oldLore = Messages.SHOP_CONTENT_TIER_ITEM_LORE
                .replace("%category%", category).replace("%content%", oldContent);
        String newName = Messages.SHOP_CONTENT_TIER_ITEM_NAME
                .replace("%category%", category).replace("%content%", newContent);
        String newLore = Messages.SHOP_CONTENT_TIER_ITEM_LORE
                .replace("%category%", category).replace("%content%", newContent);

        migrateShopValue(yml, oldName, newName, "name not set");
        migrateShopValue(yml, oldLore, newLore, "lore not set");
    }

    private static void migrateShopValue(YamlConfiguration yml, String oldPath, String newPath,
                                         String placeholder) {
        Object current = yml.get(newPath);
        Object legacy = yml.get(oldPath);
        if (isGeneratedPlaceholder(current, placeholder)) {
            yml.set(newPath, legacy == null || isGeneratedPlaceholder(legacy, placeholder) ? null : legacy);
        }
        yml.set(oldPath, null);
    }

    private static boolean isGeneratedPlaceholder(Object value, String placeholder) {
        if (value == null) return true;
        if (value instanceof String text) {
            return AdventureText.plain(AdventureText.ampersand(text))
                    .trim().equalsIgnoreCase(placeholder);
        }
        if (value instanceof List<?> values && values.size() == 1 && values.get(0) instanceof String text) {
            return isGeneratedPlaceholder(text, placeholder);
        }
        return false;
    }

    /**
     * Change a player language and refresh
     * scoreboard and custom join items.
     */
    public static boolean setPlayerLanguage(UUID uuid, String iso) {

        if (iso == null) {
            if (langByPlayer.containsKey(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    PlayerLangChangeEvent e = new PlayerLangChangeEvent(player, langByPlayer.get(uuid).iso, getDefaultLanguage().iso);
                    Bukkit.getPluginManager().callEvent(e);
                    if (e.isCancelled()) return false;
                }
            }
            langByPlayer.remove(uuid);
            return true;
        }

        iso = normalizeIso(iso);
        // Old proxy/database values are deliberately not allowed to revive
        // a language that the plugin no longer ships. Drop any stale
        // in-memory preference so the player immediately falls back to Chinese.
        if (!isSimplifiedChineseIso(iso)) {
            langByPlayer.remove(uuid);
            return false;
        }

        Language newLang = Language.getLang(iso);
        if (newLang == null) return false;
        Language oldLang = Language.getPlayerLanguage(uuid);
        if (oldLang.getIso().equals(newLang.getIso())) return false;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            PlayerLangChangeEvent e = new PlayerLangChangeEvent(player, oldLang.getIso(), newLang.getIso());
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) return false;
        }

        if (Language.getDefaultLanguage().getIso().equals(newLang.getIso())) {
            langByPlayer.remove(uuid);
            return true;
        }

        if (langByPlayer.containsKey(uuid)) {
            langByPlayer.replace(uuid, newLang);
        } else {
            langByPlayer.put(uuid, newLang);
        }
        return true;
    }

    public static String @NotNull [] getCountDownTitle(@NotNull Language playerLang, int second) {
        String[] result = new String[2];
        result[0] = AdventureText.section(AdventureText.ampersand(playerLang.getYml().get(Messages.ARENA_STATUS_START_COUNTDOWN_TITLE + "-" + second, playerLang.getString(Messages.ARENA_STATUS_START_COUNTDOWN_TITLE)).toString().replace("{second}", String.valueOf(second))));
        if (result[0].isEmpty()) {
            result[0] = " ";
        }
        result[1] = AdventureText.section(AdventureText.ampersand(playerLang.getYml().get(Messages.ARENA_STATUS_START_COUNTDOWN_SUB_TITLE + "-" + second, playerLang.getString(Messages.ARENA_STATUS_START_COUNTDOWN_SUB_TITLE)).toString().replace("{second}", String.valueOf(second))));
        if (result[1].isEmpty()) {
            result[1] = " ";
        }
        return result;
    }

    /**
     * Change server default language.
     */
    public static void setDefaultLanguage(Language defaultLanguage) {
        Language.defaultLanguage = defaultLanguage;
    }

    /**
     * Get server default language.
     */
    public static Language getDefaultLanguage() {
        return defaultLanguage;
    }
}
