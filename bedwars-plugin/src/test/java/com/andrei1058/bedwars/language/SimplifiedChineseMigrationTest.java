package com.andrei1058.bedwars.language;

import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifiedChineseMigrationTest {

    private static final List<String> BRACKETED_TEAM_PREFIX = List.of("&7[{teamColor}{teamName}&7] ");

    @Test
    void schemaSixteenKeepsGameTimeAndCustomMessages() {
        YamlConfiguration language = new YamlConfiguration();
        String customChat = "&d自定义格式 {player}：{message}";
        String customGameTime = "&b本局 {gameTime}";
        language.set(ConfigManager.CONFIG_VERSION_PATH, 14);
        language.set(Messages.FORMATTING_CHAT_SHOUT, customChat);
        language.set(Messages.FORMATTING_SB_TAB_GAME_TIME, customGameTime);
        language.addDefault(Messages.FORMATTING_SB_TAB_GAME_TIME, "&7游戏时间：&a{gameTime}");

        assertTrue(ConfigManager.applyVersionedMigration(language, 16,
                SimplifiedChinese::migrateSchema16));

        assertEquals(customChat, language.getString(Messages.FORMATTING_CHAT_SHOUT));
        assertEquals(customGameTime, language.getString(Messages.FORMATTING_SB_TAB_GAME_TIME));
        assertEquals(16, language.getInt(ConfigManager.CONFIG_VERSION_PATH));
    }

    @Test
    void schemaSixteenPersistsTheMissingGameTimeDefault() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(ConfigManager.CONFIG_VERSION_PATH, 14);
        language.addDefault(Messages.FORMATTING_SB_TAB_GAME_TIME, "&7游戏时间：&a{gameTime}");

        assertTrue(ConfigManager.applyVersionedMigration(language, 16,
                SimplifiedChinese::migrateSchema16));

        assertTrue(language.contains(Messages.FORMATTING_SB_TAB_GAME_TIME, true));
        assertEquals("&7游戏时间：&a{gameTime}",
                language.getString(Messages.FORMATTING_SB_TAB_GAME_TIME));
    }

    @Test
    void schemaTwentyOnePersistsTheMissingGameIdDefault() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set(Messages.FORMATTING_SB_TAB_GAME_ID, "&7对局编号：&f{gameId}");
        YamlConfiguration language = new YamlConfiguration();
        language.setDefaults(defaults);
        language.set(ConfigManager.CONFIG_VERSION_PATH, 20);

        assertTrue(ConfigManager.applyVersionedMigration(language, 21,
                SimplifiedChinese::migrateSchema16));

        assertEquals("&7对局编号：&f{gameId}",
                language.getString(Messages.FORMATTING_SB_TAB_GAME_ID));
        assertEquals(21, language.getInt(ConfigManager.CONFIG_VERSION_PATH));
    }

    @Test
    void arenaTabKeepsUpstreamWidthWhileLobbyIsWider() {
        assertEquals(104, SimplifiedChinese.ORIGINAL_TAB_WIDTH);
        assertEquals(104, SimplifiedChinese.ORIGINAL_TAB_WIDTH_SPACER.length());
        assertTrue(SimplifiedChinese.ORIGINAL_TAB_WIDTH_SPACER.chars()
                .allMatch(character -> character == ' '));
        assertEquals(128, SimplifiedChinese.LOBBY_TAB_WIDTH);
        assertEquals(128, SimplifiedChinese.LOBBY_TAB_WIDTH_SPACER.length());
        assertTrue(SimplifiedChinese.LOBBY_TAB_WIDTH > SimplifiedChinese.ORIGINAL_TAB_WIDTH);
    }

    @Test
    void replacesCompactTabTemplateWithOriginalRepositoryStyle() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set(Messages.FORMATTING_SB_TAB_LOBBY_FOOTER,
                List.of("", "&f当前大厅共有 {on} 名玩家", "由 {poweredBy} 提供支持,&a{serverIp}", ""));
        defaults.set(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_SUFFIX,
                List.of(" {vPrefix}", " &c&o已淘汰", " {level}", " &c&o已淘汰"));
        YamlConfiguration language = new YamlConfiguration();
        language.setDefaults(defaults);
        language.set(Messages.FORMATTING_SB_TAB_LOBBY_FOOTER,
                List.of("&7大厅人数：&f{on}", "&8{poweredBy}"));
        language.set(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_SUFFIX,
                List.of(" &7[已淘汰]"));

        SimplifiedChinese.migrateOriginalTabMessages(language);

        assertEquals(defaults.getStringList(Messages.FORMATTING_SB_TAB_LOBBY_FOOTER),
                language.getStringList(Messages.FORMATTING_SB_TAB_LOBBY_FOOTER));
        assertEquals(defaults.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_SUFFIX),
                language.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_SUFFIX));
    }

    @Test
    void preservesCustomChineseTextAndItsConfiguredWidth() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set(Messages.FORMATTING_SB_TAB_LOBBY_HEADER,
                List.of("                                                                                                        ", "&a{serverIp}", ""));
        YamlConfiguration language = new YamlConfiguration();
        language.setDefaults(defaults);
        List<String> custom = List.of("                                                                ", "&b自定义标题");
        language.set(Messages.FORMATTING_SB_TAB_LOBBY_HEADER, custom);

        SimplifiedChinese.migrateOriginalTabMessages(language);

        assertEquals(custom, language.getStringList(Messages.FORMATTING_SB_TAB_LOBBY_HEADER));
    }

    @Test
    void preservesAConfiguredTabEvenWhenItContainsALegacyWord() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set(Messages.FORMATTING_SB_TAB_STARTING_HEADER,
                List.of("&a&l{serverIp}", "&7地图：&f{map}"));
        YamlConfiguration language = new YamlConfiguration();
        language.setDefaults(defaults);
        List<String> custom = List.of("&7Map: &f{map}", "&bCustom tournament");
        language.set(Messages.FORMATTING_SB_TAB_STARTING_HEADER, custom);

        SimplifiedChinese.migrateOriginalTabMessages(language);

        assertEquals(custom, language.getStringList(Messages.FORMATTING_SB_TAB_STARTING_HEADER));
    }

    @Test
    void spectatorStartingAffixesUseTheExactUpstreamPaths() {
        assertEquals(Messages.FORMATTING_SB_TAB_STARTING_PREFIX,
                Messages.FORMATTING_SB_TAB_STARTING_PREFIX_SPEC);
        assertEquals(Messages.FORMATTING_SB_TAB_STARTING_SUFFIX,
                Messages.FORMATTING_SB_TAB_STARTING_SUFFIX_SPEC);
    }

    @Test
    void widensOnlyTheOldBuiltInLobbyHeader() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(Messages.FORMATTING_SB_TAB_LOBBY_HEADER,
                List.of(SimplifiedChinese.ORIGINAL_TAB_WIDTH_SPACER, "&a{serverIp}", ""));

        SimplifiedChinese.widenBuiltInLobbyTabHeader(language);

        assertEquals(List.of(SimplifiedChinese.LOBBY_TAB_WIDTH_SPACER, "&a{serverIp}", ""),
                language.getStringList(Messages.FORMATTING_SB_TAB_LOBBY_HEADER));
    }

    @Test
    void lobbyWidthMigrationPreservesCustomHeader() {
        YamlConfiguration language = new YamlConfiguration();
        List<String> custom = List.of("custom width", "&b自定义大厅");
        language.set(Messages.FORMATTING_SB_TAB_LOBBY_HEADER, custom);

        SimplifiedChinese.widenBuiltInLobbyTabHeader(language);

        assertEquals(custom, language.getStringList(Messages.FORMATTING_SB_TAB_LOBBY_HEADER));
    }

    @Test
    void migratesOnlyBuiltInArenaJoinAndUsesVisibleTeamNames() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(Messages.COMMAND_JOIN_PLAYER_JOIN_MSG,
                "{prefix}&7{player}&e加入了游戏(&b{on}&e/&b{max}&e)！");
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_FOOTER,
                List.of("", "&f你正在为 {teamColor}{teamName}队 &f而战", "&a{serverIp}", "&f由 {poweredBy} 提供支持", ""));
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX, List.of("{teamColor}{teamName} "));
        language.set(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                List.of("&6&l⭐ {teamColor}{teamName} "));

        SimplifiedChinese.migrateCurrentMessageDefaults(language);

        assertEquals("&e[BW] &f玩家 &b{playername} &f加入了竞技场 &a{arena}",
                language.getString(Messages.COMMAND_JOIN_PLAYER_JOIN_MSG));
        assertEquals("&f你属于 {teamColor}{teamName}队",
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_FOOTER).get(1));
        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX));
        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX));
    }

    @Test
    void preservesCustomArenaJoinAndPlayingTeamMessages() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(Messages.COMMAND_JOIN_PLAYER_JOIN_MSG, "custom join");
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_FOOTER, List.of("custom footer"));
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX, List.of("&dCUSTOM "));

        SimplifiedChinese.migrateCurrentMessageDefaults(language);

        assertEquals("custom join", language.getString(Messages.COMMAND_JOIN_PLAYER_JOIN_MSG));
        assertEquals(List.of("custom footer"),
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_FOOTER));
        assertEquals(List.of("&dCUSTOM "),
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX));
    }

    @Test
    void exposesEveryKnownBuiltInTeamLabel() {
        YamlConfiguration language = new YamlConfiguration();
        for (String path : List.of(
                Messages.FORMATTING_SB_TAB_PLAYING_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX)) {
            language.set(path, path.equals(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX)
                    || path.equals(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX)
                    ? List.of("&6&l⭐ {teamColor}{teamName} ")
                    : List.of("{teamColor}{teamName} "));
        }

        Language.migrateBuiltInTabPlayerRows(language);

        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX));
        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX));
        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX));
        assertEquals(BRACKETED_TEAM_PREFIX,
                language.getStringList(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX));
    }

    @Test
    void migratesBuiltInEliminatedTabFormatAndPreservesCustomValues() {
        YamlConfiguration builtIn = new YamlConfiguration();
        builtIn.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_PREFIX, List.of(""));
        builtIn.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX, List.of(" &c[淘汰]"));

        SimplifiedChinese.migrateEliminatedTabFormat(builtIn);

        assertEquals(SimplifiedChinese.ELIMINATED_TAB_PREFIX,
                builtIn.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_PREFIX));
        assertEquals(SimplifiedChinese.ELIMINATED_TAB_SUFFIX,
                builtIn.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX));

        YamlConfiguration custom = new YamlConfiguration();
        custom.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_PREFIX, List.of("&dCUSTOM "));
        custom.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX, List.of(" &eCUSTOM"));
        SimplifiedChinese.migrateEliminatedTabFormat(custom);

        assertEquals(List.of("&dCUSTOM "),
                custom.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_PREFIX));
        assertEquals(List.of(" &eCUSTOM"),
                custom.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX));
    }

    @Test
    void migratesLegacyTrackingMessageAndPreservesCustomValues() {
        YamlConfiguration builtIn = new YamlConfiguration();
        builtIn.set(Messages.FORMATTING_ACTION_BAR_TRACKING,
                "&f正在追踪：{team} &f- 距离：{distance}m");

        SimplifiedChinese.migrateTrackingMessage(builtIn);

        assertEquals("&f正在追踪：{player} &f- 距离：{distance}m",
                builtIn.getString(Messages.FORMATTING_ACTION_BAR_TRACKING));

        YamlConfiguration custom = new YamlConfiguration();
        custom.set(Messages.FORMATTING_ACTION_BAR_TRACKING, "&b自定义追踪");
        SimplifiedChinese.migrateTrackingMessage(custom);

        assertEquals("&b自定义追踪",
                custom.getString(Messages.FORMATTING_ACTION_BAR_TRACKING));
    }

    @Test
    void addsArenaInviteToTheUnchangedBuiltInCommandHelp() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(Messages.COMMAND_MAIN, List.of("", "&2▪ &7/bw stats", "&2▪ &7/bw join &o<游戏/模式>",
                "&2▪ &7/bw leave", "&2▪ &7/bw lang", "&2▪ &7/bw gui", "&2▪ &7/bw start &3（赞助者）"));

        SimplifiedChinese.migrateCommandHelp(language, "bw");

        assertTrue(language.getStringList(Messages.COMMAND_MAIN).contains("&2▪ &7/bw invite &o<大厅或未开局竞技场玩家>"));
    }
}
