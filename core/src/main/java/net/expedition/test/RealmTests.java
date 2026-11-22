package net.expedition.test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.expedition.Expedition;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmLayout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The realm layout: spawn is Overworld, every realm lies within ~9000 blocks of spawn, border weights are sane. */
@GameTestHolder(Expedition.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RealmTests {
    private RealmTests() {
    }

    /** The map's sketch of unexplored land: a biome per chunk and the surface height on the coarse grid, in bounds. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void previewRegionCarriesHeights(GameTestHelper helper) {
        var level = helper.getLevel();
        long start = System.nanoTime();
        var region = folk.sisby.surveyor.preview.BiomePreview.compute(level, 3, -2);
        long ms = (System.nanoTime() - start) / 1_000_000;
        int grid = folk.sisby.surveyor.preview.BiomePreview.HEIGHT_GRID;
        helper.assertTrue(region.heights().size() == grid * grid + 1 && region.seaLevel() == level.getChunkSource().getGenerator().getSeaLevel(), "heights: " + region.heights().size());
        helper.assertTrue(region.biomes().length == folk.sisby.surveyor.preview.BiomePreview.QUARTS * folk.sisby.surveyor.preview.BiomePreview.QUARTS, "biome samples: " + region.biomes().length);
        helper.assertTrue(region.heights().stream().allMatch(y -> y >= level.getMinBuildHeight() && y <= level.getMaxBuildHeight()),
            "height out of the world: " + region.heights());
        helper.assertTrue(!region.palette().isEmpty(), "no biomes");
        Expedition.LOG.info("Preview region: {} biomes, heights {}..{}, {} ms", region.palette().size(),
            region.heights().stream().mapToInt(Integer::intValue).min().orElse(0), region.heights().stream().mapToInt(Integer::intValue).max().orElse(0), ms);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void layoutKeepsSpawnAndRingsEveryRealm(GameTestHelper helper) {
        long key = RealmLayout.key();
        List<Realm> realms = RealmLayout.realms();
        try {
            for (long seed : new long[] {1, 42, 20261118, -7}) {
                RealmLayout.configureKey(seed * 0x9E3779B97F4A7C15L, List.of(Realm.values()));
                Set<Realm> near = EnumSet.noneOf(Realm.class);
                for (int x = -9000; x <= 9000; x += 120) {
                    for (int z = -9000; z <= 9000; z += 120) {
                        RealmLayout.Sample sample = RealmLayout.sample(x, z);
                        helper.assertTrue(sample.weight() >= 0.5 && sample.weight() <= 1, "weight out of range at " + x + "," + z);
                        if (x * x + z * z < 1700 * 1700) {
                            helper.assertTrue(sample.first() == Realm.OVERWORLD && sample.weight() == 1,
                                "not Overworld near spawn at " + x + "," + z + " (seed " + seed + ")");
                        }
                        near.add(sample.first());
                    }
                }
                helper.assertTrue(near.size() == Realm.values().length, "realms within 9000 of spawn: " + near + " (seed " + seed + ")");
            }
        } finally {
            RealmLayout.configureKey(key, realms);
        }
        helper.succeed();
    }

    /** The disc: sea along every strait and the rim, land deep in a continent, the Overworld's sea beyond the rim. */
    @GameTest(template = "empty")
    public static void discHasStraitsAndARim(GameTestHelper helper) {
        long key = RealmLayout.key();
        List<Realm> realms = RealmLayout.realms();
        try {
            RealmLayout.configureKey(7 * 0x9E3779B97F4A7C15L, List.of(Realm.values()));
            int straits = 0, inland = 0;
            for (int x = -14000; x <= 14000; x += 250) {
                for (int z = -14000; z <= 14000; z += 250) {
                    RealmLayout.Sample sample = RealmLayout.sample(x, z);
                    double shape = net.expedition.world.realm.Continents.shape(sample);
                    if (sample.radius() > 1800 && sample.edge() < RealmLayout.STRAIT) {
                        helper.assertTrue(shape < -0.2 && sample.first() == Realm.OVERWORLD, "strait not sea at " + x + "," + z + ": " + shape);
                        straits++;
                    }
                    if (sample.edge() > RealmLayout.STRAIT + 600 && sample.rim() > 600) {
                        helper.assertTrue(shape > 0.1, "inland not land at " + x + "," + z + ": " + shape);
                        inland++;
                    }
                }
            }
            // The round world ends in open sea: the square's corners are sea, out to the border.
            RealmLayout.Sample corner = RealmLayout.sample(14200, 14200);
            helper.assertTrue(corner.square() < RealmLayout.RADIUS && net.expedition.world.realm.Continents.shape(corner) < -0.45, "a corner of the square is not open sea");
            RealmLayout.Sample beyond = RealmLayout.sample(RealmLayout.RADIUS + 50, 0);
            helper.assertTrue(beyond.first() == Realm.OVERWORLD && net.expedition.world.realm.Continents.shape(beyond) < -0.45, "no deep sea at the rim");
            helper.assertTrue(straits > 20 && inland > 1000, "straits " + straits + ", inland " + inland);
        } finally {
            RealmLayout.configureKey(key, realms);
        }
        helper.succeed();
    }
}
