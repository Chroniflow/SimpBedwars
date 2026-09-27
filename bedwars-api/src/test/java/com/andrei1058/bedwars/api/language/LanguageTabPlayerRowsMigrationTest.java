package com.andrei1058.bedwars.api.language;

import com.andrei1058.bedwars.api.configuration.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTabPlayerRowsMigrationTest {

    private static final List<String> BRACKETED_TEAM_PREFIX = List.of("&7[{teamColor}{teamName}&7] ");

    @Test
    void schemaEightEmptyBuiltInPrefixesGainVisibleTeamNames() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(ConfigManager.CONFIG_VERSION_PATH, 8);
        for (String path : List.of(
                Messages.FORMATTING_SB_TAB_PLAYING_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX)) {
            language.set(path, List.of(""));
        }

        assertTrue(ConfigManager.applyVersionedMigration(language, 9,
                Language::migrateBuiltInTabPlayerRows));

        for (String path : List.of(
                Messages.FORMATTING_SB_TAB_PLAYING_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX)) {
            assertEquals(BRACKETED_TEAM_PREFIX, language.getStringList(path), path);
        }
    }

    @Test
    void olderSchemaMigratesKnownBuiltInTeamLabelsToVisibleTeamNames() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(ConfigManager.CONFIG_VERSION_PATH, 5);
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_PREFIX,
                List.of("{teamColor}{teamName} "));
        language.set(Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                List.of("&6&l⭐ {teamColor}{teamName} "));
        language.set(Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX,
                List.of("&6★ {teamColor}[{teamLetter}] "));
        language.set(Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX,
                List.of("{teamColor}[{teamLetter}] "));
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX,
                List.of(" &c&oEliminated {teamColor}&o{teamName}",
                        " {teamColor}&oEliminated {vPrefix}",
                        "{teamColor}&oEliminated {level}"));

        assertTrue(ConfigManager.applyVersionedMigration(language, 9,
                Language::migrateBuiltInTabPlayerRows));

        for (String path : List.of(
                Messages.FORMATTING_SB_TAB_PLAYING_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX,
                Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX)) {
            assertEquals(BRACKETED_TEAM_PREFIX, language.getStringList(path), path);
        }
        assertEquals(List.of(" &c&oEliminated", " {teamColor}&oEliminated {vPrefix}",
                        "{teamColor}&oEliminated {level}"),
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX));
        assertEquals(9, language.getInt(ConfigManager.CONFIG_VERSION_PATH));
    }

    @Test
    void migrationPreservesAdministratorPlayerRows() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(ConfigManager.CONFIG_VERSION_PATH, 5);
        Map<String, List<String>> customPrefixes = Map.of(
                Messages.FORMATTING_SB_TAB_PLAYING_PREFIX, List.of("&d[联赛] {teamColor}{teamName} "),
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN1_PREFIX, List.of("&6冠军 {teamName} "),
                Messages.FORMATTING_SB_TAB_RESTARTING_WIN2_PREFIX, List.of("&7已淘汰冠军 {teamLetter} "),
                Messages.FORMATTING_SB_TAB_RESTARTING_ELM_PREFIX, List.of("&c败方 {teamColor} ")
        );
        List<String> customSuffix = List.of(" &e自定义");
        customPrefixes.forEach(language::set);
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX, customSuffix);

        assertTrue(ConfigManager.applyVersionedMigration(language, 9,
                Language::migrateBuiltInTabPlayerRows));

        customPrefixes.forEach((path, expected) -> assertEquals(expected, language.getStringList(path), path));
        assertEquals(customSuffix, language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX));
    }

    @Test
    void historicalChineseEliminatedRowDoesNotBecomeEnglish() {
        YamlConfiguration language = new YamlConfiguration();
        language.set(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX,
                List.of(" &c&o已淘汰 {teamColor}&o{teamName}",
                        " {teamColor}&o已淘汰 {vPrefix}",
                        "{teamColor}&o已淘汰 {level}"));

        Language.migrateBuiltInTabPlayerRows(language);

        assertEquals(List.of(""),
                language.getStringList(Messages.FORMATTING_SB_TAB_PLAYING_ELM_SUFFIX));
    }
}
