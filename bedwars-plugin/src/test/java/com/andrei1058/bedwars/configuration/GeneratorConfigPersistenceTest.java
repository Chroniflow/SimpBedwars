package com.andrei1058.bedwars.configuration;

import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratorConfigPersistenceTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesEmptyIslandSettingDuringUpgradeAndRestart(boolean enabled) throws Exception {
        Path file = directory.resolve("arena.yml");
        Files.writeString(file, "config-version: 22\ndisable-generator-for-empty-teams: " + enabled
                + "\ncustom-option: retained\n");
        Plugin plugin = plugin();

        ArenaConfig config = new ArenaConfig(plugin, "arena", directory.toString());
        config.save();
        ArenaConfig restarted = new ArenaConfig(plugin, "arena", directory.toString());
        restarted.save();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(enabled, saved.getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS));
        assertEquals(24, saved.getInt("config-version"));
        assertEquals("retained", saved.getString("custom-option"));
        assertTrue(saved.getComments(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS).getFirst().contains("无成员"));
        String text = Files.readString(file);
        assertTrue(text.indexOf("disable-generator-for-empty-teams:") < text.indexOf("stop-generator-when-no-team-on-island:"));
        assertTrue(text.indexOf("stop-generator-when-no-team-on-island:") < text.indexOf("disable-npcs-for-empty-teams:"));
    }

    @Test
    void missingEmptyIslandSettingDefaultsToEnabled() throws Exception {
        Files.writeString(directory.resolve("arena.yml"), "config-version: 22\n");

        ArenaConfig config = new ArenaConfig(plugin(), "arena", directory.toString());

        assertTrue(config.getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS));
        config.reload();
        assertTrue(config.getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesOnIslandGeneratorSettingDuringUpgradeAndRestart(boolean enabled) throws Exception {
        Path file = directory.resolve("arena.yml");
        Files.writeString(file, "config-version: 23\n"
                + ConfigPath.ARENA_STOP_GENERATOR_WITHOUT_TEAM_ON_ISLAND + ": " + enabled + "\n");

        Plugin plugin = plugin();
        ArenaConfig config = new ArenaConfig(plugin, "arena", directory.toString());
        config.save();
        new ArenaConfig(plugin, "arena", directory.toString()).save();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(24, saved.getInt("config-version"));
        assertEquals(enabled, saved.getBoolean(ConfigPath.ARENA_STOP_GENERATOR_WITHOUT_TEAM_ON_ISLAND));
    }

    @Test
    void missingOnIslandGeneratorSettingDefaultsToEnabled() throws Exception {
        Files.writeString(directory.resolve("arena.yml"), "config-version: 23\n");

        ArenaConfig config = new ArenaConfig(plugin(), "arena", directory.toString());

        assertTrue(config.getBoolean(ConfigPath.ARENA_STOP_GENERATOR_WITHOUT_TEAM_ON_ISLAND));
    }

    @Test
    void preservesCustomRatesGroupsAndLegacyLimitsDuringCommentMigration() throws Exception {
        Path file = directory.resolve("generators.yml");
        Files.writeString(file, """
                config-version: 5
                Default:
                  iron:
                    delay: 2
                    amount: 9
                    spawn-limit: 123
                  gold:
                    delay: 6
                Solo:
                  diamond:
                    tierI:
                      delay: 8
                      spawn-limit: 55
                """);
        Plugin plugin = plugin();

        new GeneratorsConfig(plugin, "generators", directory.toString());
        new GeneratorsConfig(plugin, "generators", directory.toString());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(6, saved.getInt("config-version"));
        assertEquals(2, saved.getInt("Default.iron.delay"));
        assertEquals(6, saved.getInt("Default.gold.delay"));
        assertEquals(9, saved.getInt("Default.iron.amount"));
        assertEquals(123, saved.getInt("Default.iron.spawn-limit"));
        assertEquals(8, saved.getInt("Solo.diamond.tierI.delay"));
        assertEquals(55, saved.getInt("Solo.diamond.tierI.spawn-limit"));
        assertTrue(saved.getComments("Default.iron.spawn-limit").getFirst().contains("历史兼容"));
    }

    private Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("SimpMC-BedWars");
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("SimpMC-BedWars", "test", "unused"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        return plugin;
    }
}
