package com.andrei1058.bedwars.arena;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.generator.GeneratorType;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigManager;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OreGeneratorProductionTest {
    private BedWars previousPlugin;
    private MockedStatic<BedWars> bedWars;
    private IArena arena;
    private ITeam team;
    private ConfigManager arenaConfig;
    private World world;
    private final List<Player> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        previousPlugin = BedWars.plugin;
        BedWars.plugin = mock(BedWars.class);
        when(BedWars.plugin.getConfig()).thenReturn(new YamlConfiguration());
        ConfigManager generators = mock(ConfigManager.class);
        when(generators.getYml()).thenReturn(new YamlConfiguration());
        when(generators.getInt(anyString())).thenReturn(1);
        bedWars = mockStatic(BedWars.class);
        bedWars.when(BedWars::getGeneratorsCfg).thenReturn(generators);
        arenaConfig = mock(ConfigManager.class);
        when(arenaConfig.getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS)).thenReturn(true);
        arena = mock(IArena.class);
        when(arena.getConfig()).thenReturn(arenaConfig);
        when(arena.getStatus()).thenReturn(GameState.playing);
        when(arena.getRegionsList()).thenReturn(new ArrayList<>());
        team = mock(ITeam.class);
        when(team.getMembers()).thenReturn(members);
        world = mock(World.class);
    }

    @AfterEach
    void tearDown() {
        try {
            if (bedWars != null) bedWars.close();
        } finally {
            BedWars.plugin = previousPlugin;
        }
    }

    @Test
    void unassignedIslandDoesNotProduceAfterItsBedIsRemovedAtStart() {
        when(team.isBedDestroyed()).thenReturn(true);
        CountingGenerator generator = generator(GeneratorType.IRON, team);

        generator.spawn();
        generator.spawn();

        assertEquals(0, generator.drops);
        assertEquals(1, generator.getNextSpawn());
        verifyNoInteractions(world);
    }

    @Test
    void productionStopsOnlyAfterLastMemberIsEliminated() {
        Player player = mock(Player.class);
        members.add(player);
        when(team.isBedDestroyed()).thenReturn(true);
        CountingGenerator generator = generator(GeneratorType.GOLD, team);
        generator.spawn();
        assertEquals(1, generator.drops);

        members.clear();
        generator.spawn();
        assertEquals(1, generator.drops);

        members.add(player);
        generator.spawn();
        assertEquals(2, generator.drops);
    }

    @Test
    void travelAndRespawnWaitDoNotStopAnActiveTeamsProduction() {
        Player player = mock(Player.class);
        members.add(player);
        when(arena.isReSpawning(player)).thenReturn(true);
        CountingGenerator generator = generator(GeneratorType.IRON, team);

        generator.spawn();
        generator.spawn();

        assertEquals(2, generator.drops);
        verifyNoInteractions(player);
    }

    @Test
    void temporarilyDisconnectedTeamWithABedKeepsProducing() {
        CountingGenerator generator = generator(GeneratorType.GOLD, team);

        generator.spawn();

        assertEquals(1, generator.drops);
    }

    @Test
    void existingFalseSettingKeepsEmptyIslandProductionEnabled() {
        when(arenaConfig.getBoolean(ConfigPath.ARENA_DISABLE_GENERATOR_FOR_EMPTY_TEAMS)).thenReturn(false);
        when(team.isBedDestroyed()).thenReturn(true);
        CountingGenerator generator = generator(GeneratorType.IRON, team);

        generator.spawn();

        assertEquals(1, generator.drops);
    }

    @ParameterizedTest
    @EnumSource(value = GeneratorType.class, names = {"IRON", "GOLD", "DIAMOND", "EMERALD"})
    void spawnLimitStopsProductionUntilTheGroundItemsAreCollected(GeneratorType type) {
        CountingGenerator generator = generator(type, null);
        Item backlog = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        Material material = generator.getOre().getType();
        when(backlog.getItemStack()).thenReturn(stack);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(64);
        when(world.getNearbyEntitiesByType(eq(Item.class), any(Location.class),
                anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(List.of(backlog));

        generator.setSpawnLimit(1);
        generator.spawn();
        generator.spawn();

        assertEquals(0, generator.drops);
        assertEquals(1, generator.getSpawnLimit());
        verify(world, atLeastOnce()).getNearbyEntitiesByType(eq(Item.class), any(Location.class),
                anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void productionResumesWhenTheGroundItemsStayBelowTheLimit() {
        CountingGenerator generator = generator(GeneratorType.IRON, null);
        when(world.getNearbyEntitiesByType(eq(Item.class), any(Location.class),
                anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(List.of());

        generator.setSpawnLimit(4);
        generator.spawn();

        assertEquals(1, generator.drops);
    }

    @Test
    void upgradedTeamEmeraldGeneratorStopsWithItsTeam() {
        members.add(mock(Player.class));
        CountingGenerator generator = generator(GeneratorType.CUSTOM, team);
        generator.setType(GeneratorType.EMERALD);
        generator.setAmount(3);
        generator.setSpawnLimit(1);
        generator.spawn();
        assertEquals(3, generator.produced);

        when(team.isBedDestroyed()).thenReturn(true);
        members.clear();
        generator.spawn();
        assertEquals(3, generator.produced);
    }

    @ParameterizedTest
    @EnumSource(value = GameState.class, names = {"waiting", "starting", "restarting"})
    void noResourcesAreProducedOutsideAnActiveRound(GameState state) {
        when(arena.getStatus()).thenReturn(state);
        CountingGenerator generator = generator(GeneratorType.DIAMOND, null);

        generator.spawn();

        assertEquals(0, generator.drops);
    }

    private CountingGenerator generator(GeneratorType type, ITeam owner) {
        // Paper ItemStack construction requires a live registry. Only replace
        // that boundary; the generator constructor, config and spawn path run.
        try (var itemStacks = mockConstruction(ItemStack.class, (item, context) ->
                when(item.getType()).thenReturn((Material) context.arguments().getFirst()))) {
            return new CountingGenerator(new Location(world, 0, 65, 0), arena, type, owner);
        }
    }

    private static final class CountingGenerator extends OreGenerator {
        private int drops;
        private int produced;

        private CountingGenerator(Location location, IArena arena, GeneratorType type, ITeam team) {
            super(location, arena, type, team);
        }

        @Override
        public void dropItem(Location location) {
            drops++;
            produced += getAmount();
        }
    }
}
