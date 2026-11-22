package net.expedition.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.expedition.Expedition;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmBiomeSource;
import net.expedition.world.realm.RealmLayout;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Every story dungeon has a land of its own, as the Twilight Forest gives each of its bosses a biome: the step's
 * {@code home_biomes} (most fitting first), a different one for every dungeon while a free one is left.
 * <ul>
 * <li>A realm's dungeon (Nether, Twilight, End, Aether): its biome becomes a land of its own (RealmBiomeSource.Land, some
 * 800 blocks across) at a spot deep in that realm, from the step's ring outward, the dungeon in its middle. A realm cell
 * holds one biome; without this every dungeon of a realm stood in the same one.</li>
 * <li>An Overworld dungeon: a spot of its biome in its ring where the Overworld has one (by climate); else, on dry
 * land, a land of it is made there too (the terrain stays the Overworld's, its caves too).</li>
 * </ul>
 * All from the seed, worked out when the Overworld loads (before any chunk generates): the same world gets the same
 * lands. Only for worlds made with lands (a world older than them keeps its biomes: no seams through its land). A step
 * without a fitting biome is placed as before.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class HomeBiomes {
    private HomeBiomes() {
    }

    /** Where the dungeon's land is (its middle, or a spot of its biome) and which biome it is. */
    public record Site(BlockPos pos, Holder<Biome> biome) {
    }

    /** Overworld survey spacing; how far past the ring a biome is looked for (first a little, then much further). */
    private static final int STEP = 160;
    private static final double REACH = 1.3;
    private static final double FAR_REACH = 1.4;
    /** No story dungeon lies further than this from its branch's middle (about 10 km: the whole route on foot and by ship). */
    private static final int SURVEY_MAX = net.expedition.world.realm.RealmLayout.RADIUS - net.expedition.world.realm.RealmLayout.RIM_SEA;
    /** Land scan spacing, and how far apart two lands' middles are at least. */
    private static final int LAND_STEP = 128;
    private static final int LAND_SPACING = RealmBiomeSource.LAND_RADIUS * 3;
    /** Of a land's 12 rim points, this many lie in its realm (and on dry land, in the Overworld). */
    private static final int RIM_NEEDED = 9;
    /** ...or this many, where the land is watery (a land is only drawn on its biome's ground, so water at its rim stays water). */
    private static final int RIM_LOOSE = 6;
    /** Overworld dungeons keep this far apart. */
    private static final int SPACING = 600;

    /** Per story branch (0: around spawn; see Branches), each step's land. */
    private static Map<Integer, Map<String, Site>> sites;
    /** The lands of every branch, as the biome source has them. */
    private static final List<RealmBiomeSource.Land> LANDS = new ArrayList<>();
    private static boolean withLands;

    /** The step's land on the spawn branch, or null (no fitting biome: the step is placed as before). */
    public static Site of(ServerLevel level, String stepId) {
        return of(level, 0, stepId);
    }

    /** The step's land on a branch, or null. */
    public static Site of(ServerLevel level, int branch, String stepId) {
        ensure(level);
        return sites.getOrDefault(branch, Map.of()).get(stepId);
    }

    /** A new branch's lands, around its middle (Branches): made now, before its chunks generate. */
    public static void addBranch(ServerLevel level, int branch, BlockPos middle) {
        ensure(level);
        sites.put(branch, assign(level, middle));
    }

    private static void ensure(ServerLevel level) {
        if (sites != null) {
            return;
        }
        sites = new HashMap<>();
        LANDS.clear();
        withLands = madeWithLands(level);
        if (!withLands) {
            Expedition.LOG.info("Home biomes: a world older than them, dungeons placed as before");
        }
        boolean realmWorld = level.getChunkSource().getGenerator().getBiomeSource() instanceof RealmBiomeSource;
        sites.put(0, assign(level, realmWorld ? new BlockPos(RealmLayout.originX(), 64, RealmLayout.originZ()) : level.getSharedSpawnPos()));
        List<BlockPos> branches = RouteData.get(level).branches;
        for (int i = 0; i < branches.size(); i++) {
            sites.put(i + 1, assign(level, branches.get(i)));
        }
    }

    @SubscribeEvent
    static void onLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ensure(level);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        sites = null;
        LANDS.clear();
    }

    /** Marks a world made with story lands (a new world gets the mark on its first load). */
    private static final class Mark extends SavedData {
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("lands", true);
            return tag;
        }
    }

    private static boolean madeWithLands(ServerLevel level) {
        var storage = level.getDataStorage();
        var factory = new SavedData.Factory<Mark>(() -> null, (tag, registries) -> new Mark(), null);
        if (storage.get(factory, "expedition_lands") != null) {
            return true;
        }
        if (level.getGameTime() > 0) {
            return false; // played before lands existed
        }
        Mark mark = new Mark();
        mark.setDirty();
        storage.set("expedition_lands", mark);
        return true;
    }

    /** One branch's lands around its middle ({@code spawn}): the spawn branch's, or a later one's. */
    private static Map<String, Site> assign(ServerLevel level, BlockPos spawn) {
        long began = System.currentTimeMillis();
        if (!withLands) {
            return Map.of();
        }
        RealmBiomeSource realms = level.getChunkSource().getGenerator().getBiomeSource() instanceof RealmBiomeSource source ? source : null;
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, Site> result = new HashMap<>();
        Set<ResourceKey<Biome>> taken = new HashSet<>();
        List<RealmBiomeSource.Land> lands = LANDS; // every branch's: a new land keeps clear of them all
        List<BlockPos> chosen = new ArrayList<>();
        List<RouteSteps.Entry> overworldSteps = new ArrayList<>();
        List<RouteSteps.Entry> steps = new ArrayList<>(RouteSteps.all());
        steps.sort(Comparator.comparingInt(entry -> entry.step().order()));
        for (RouteSteps.Entry entry : steps) {
            RouteStep step = entry.step();
            if (step.structures().isEmpty() || BossLair.applies(step) || step.homeBiomes().isEmpty()) {
                continue;
            }
            Realm realm = RealmWorld.realmFor(level, step.dimension());
            if (realm == null ? !step.dimension().equals(Level.OVERWORLD.location()) : realm != Realm.OVERWORLD && !RealmLayout.realms().contains(realm)) {
                continue; // its own dimension (not the realm world), or a realm this world doesn't carry
            }
            if (realm == null || realm == Realm.OVERWORLD) {
                overworldSteps.add(entry);
                continue;
            }
            // A realm's dungeon: a land of its biome.
            List<Holder<Biome>> wanted = new ArrayList<>();
            for (ResourceLocation id : step.homeBiomes()) {
                biomes.getHolder(ResourceKey.create(Registries.BIOME, id)).filter(biome -> realms.isLand(realm, biome)).ifPresent(wanted::add);
            }
            if (wanted.isEmpty()) {
                Expedition.LOG.warn("Home biomes: none of {} is a land of {} for {}", step.homeBiomes(), realm, entry.id().getPath());
                continue;
            }
            Holder<Biome> biome = wanted.stream().filter(b -> !taken.contains(key(b))).findFirst().orElse(wanted.get(0));
            BlockPos middle = landSpot(level, realm, step, spawn, lands, chosen, SURVEY_MAX, RIM_NEEDED, entry.id().getPath().hashCode());
            if (middle == null) {
                Expedition.LOG.info("Home biomes: no room in {} for the land of {}, placed as before", realm, entry.id().getPath());
                continue;
            }
            taken.add(key(biome));
            lands.add(new RealmBiomeSource.Land(realm, middle.getX(), middle.getZ(), biome));
            result.put(entry.id().getPath(), new Site(middle, biome));
        }
        if (!overworldSteps.isEmpty()) {
            // The survey reads the Overworld's own biomes: no Overworld land is set yet.
            Map<ResourceKey<Biome>, List<BlockPos>> seen = survey(level, spawn, overworldSteps);
            for (RouteSteps.Entry entry : overworldSteps) {
                RouteStep step = entry.step();
                List<Holder<Biome>> wanted = new ArrayList<>();
                for (ResourceLocation id : step.homeBiomes()) {
                    biomes.getHolder(ResourceKey.create(Registries.BIOME, id)).filter(biome -> fitsTags(step, biome)).ifPresent(wanted::add);
                }
                // A free biome in the ring, a free one a little further out; else, on land, a land of the first free one;
                // else one another dungeon has, elsewhere.
                Site site = natural(level, step, wanted, seen, spawn, chosen, taken, REACH, false);
                // The first chapters are walked: rather a land of its biome near its ring than its biome far away.
                boolean early = Acts.actOf(step) <= 2;
                if (site == null && !early) {
                    site = natural(level, step, wanted, seen, spawn, chosen, taken, FAR_REACH, false);
                }
                if (site == null && realms != null && !wanted.isEmpty()) {
                    Holder<Biome> biome = wanted.stream().filter(b -> !taken.contains(key(b))).findFirst().orElse(wanted.get(0));
                    int salt = entry.id().getPath().hashCode();
                    BlockPos middle = watery(biome) ? null
                        : landSpot(level, Realm.OVERWORLD, step, spawn, lands, chosen, early ? step.maxDistance() * 2 : SURVEY_MAX, RIM_NEEDED, salt);
                    if (middle == null && !watery(biome)) {
                        // A watery land: a land whose rim may touch more water (for the first chapters a little further
                        // only), rather than none.
                        middle = landSpot(level, Realm.OVERWORLD, step, spawn, lands, chosen, early ? step.maxDistance() * 4 : SURVEY_MAX, RIM_LOOSE, salt);
                    }
                    if (middle != null) {
                        lands.add(new RealmBiomeSource.Land(Realm.OVERWORLD, middle.getX(), middle.getZ(), biome));
                        site = new Site(middle, biome);
                    }
                }
                if (site == null && early) {
                    site = natural(level, step, wanted, seen, spawn, chosen, taken, FAR_REACH, false);
                }
                if (site == null) {
                    site = natural(level, step, wanted, seen, spawn, chosen, taken, REACH, true);
                }
                if (site == null) {
                    site = natural(level, step, wanted, seen, spawn, chosen, taken, FAR_REACH, true);
                }
                if (site == null) {
                    Expedition.LOG.info("Home biomes: none of {} around for {}, placed as before", step.homeBiomes(), entry.id().getPath());
                    continue;
                }
                taken.add(key(site.biome()));
                chosen.add(site.pos());
                result.put(entry.id().getPath(), site);
            }
        }
        if (realms != null) {
            cover(level, realms, spawn, lands, chosen);
            realms.setStoryLands(lands);
        }
        Expedition.LOG.info("Home biomes ({} ms, {} lands made, spawn {}): {}", System.currentTimeMillis() - began, lands.size(), spawn.toShortString(),
            result.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue().biome().getRegisteredName() + "@" + e.getValue().pos().getX() + "," + e.getValue().pos().getZ()
                    + " (" + (int) distance(e.getValue().pos(), spawn) + ")").toList());
        return result;
    }

    /** Random spots a biome missing from the disc tries for its land. */
    private static final int COVER_TRIES = 2000;
    /** A biome's land for the disc's sake keeps this far from the others (they may touch: they only add a biome). */
    private static final int COVER_SPACING = RealmBiomeSource.LAND_RADIUS * 5 / 2;

    /**
     * Every land biome on the disc: the Overworld's (its climate's, on dry land) and every realm's (its cells' one
     * biome each). Those its continents show nowhere get a land of their own, somewhere on a continent of their realm
     * away from the others: nothing a mod brings is missing from the world.
     */
    private static void cover(ServerLevel level, RealmBiomeSource realms, BlockPos spawn, List<RealmBiomeSource.Land> lands, List<BlockPos> chosen) {
        int radius = SURVEY_MAX - (int) (RealmBiomeSource.LAND_RADIUS * 1.25);
        Map<Realm, Set<ResourceKey<Biome>>> present = new java.util.EnumMap<>(Realm.class);
        for (int x = -radius; x <= radius; x += STEP) {
            for (int z = -radius; z <= radius; z += STEP) {
                if ((double) x * x + (double) z * z > (double) radius * radius) {
                    continue;
                }
                int bx = spawn.getX() + x;
                int bz = spawn.getZ() + z;
                RealmLayout.Sample sample = RealmLayout.sample(bx, bz);
                Holder<Biome> biome = sample.first() == Realm.OVERWORLD ? surfaceBiome(level, bx, bz) : realms.landAt(sample.first(), sample.cell(), bx, bz);
                if (biome != null) {
                    biome.unwrapKey().ifPresent(key -> present.computeIfAbsent(sample.first(), k -> new HashSet<>()).add(key));
                }
            }
        }
        lands.forEach(land -> land.biome().unwrapKey().ifPresent(key -> present.computeIfAbsent(land.realm(), k -> new HashSet<>()).add(key)));
        int made = 0;
        List<String> left = new ArrayList<>();
        for (Realm realm : RealmLayout.realms()) {
            List<Holder<Biome>> wanted = realm == Realm.OVERWORLD
                ? realms.overworldBiomes().stream().filter(biome -> !watery(biome) && !RealmBiomeSource.underground(biome)).toList()
                : realms.lands(realm);
            List<Holder<Biome>> missing = wanted.stream().filter(biome -> !present.getOrDefault(realm, Set.of()).contains(key(biome)))
                .sorted(Comparator.comparing(biome -> key(biome).location().toString())).toList();
            for (Holder<Biome> biome : missing) {
                BlockPos middle = coverSpot(level, realm, spawn, radius, lands, chosen, key(biome).location().hashCode());
                if (middle == null) {
                    left.add(key(biome).location().toString());
                    continue;
                }
                lands.add(new RealmBiomeSource.Land(realm, middle.getX(), middle.getZ(), biome));
                made++;
            }
        }
        Expedition.LOG.info("Home biomes: {} lands for biomes the disc lacked{}", made, left.isEmpty() ? "" : "; no room for " + left);
    }

    /** A random spot of a realm's continent for a land: the whole land deep in it (on dry land, in the Overworld), clear of the others. */
    private static BlockPos coverSpot(ServerLevel level, Realm realm, BlockPos spawn, int radius, List<RealmBiomeSource.Land> lands, List<BlockPos> chosen, int salt) {
        java.util.Random random = new java.util.Random(level.getSeed() ^ salt);
        double edge = RealmBiomeSource.LAND_RADIUS * 1.25 + 120;
        for (int attempt = 0; attempt < COVER_TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = spawn.getX() + (int) (Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) (Math.sin(angle) * distance);
            if (distance < edge + 150 || !fits(level, realm, x, z)
                || lands.stream().anyMatch(land -> sq(land.x() - x) + sq(land.z() - z) < sq(COVER_SPACING))
                || chosen.stream().anyMatch(other -> sq(other.getX() - x) + sq(other.getZ() - z) < sq(LAND_SPACING))) {
                continue;
            }
            int rim = 0;
            for (int k = 0; k < 12; k++) {
                double a = Math.PI * 2 * k / 12;
                rim += fits(level, realm, x + (int) (Math.cos(a) * edge), z + (int) (Math.sin(a) * edge)) ? 1 : 0;
            }
            BlockPos middle = new BlockPos(x, spawn.getY(), z);
            if (rim >= RIM_NEEDED && realm.hasGround(level, middle)) {
                return middle;
            }
        }
        return null;
    }

    /** The spot of a wanted biome (only free ones unless {@code shared}) in the ring, nearest its middle, away from the others. */
    private static Site natural(ServerLevel level, RouteStep step, List<Holder<Biome>> wanted, Map<ResourceKey<Biome>, List<BlockPos>> seen, BlockPos spawn,
                                List<BlockPos> chosen, Set<ResourceKey<Biome>> taken, double reach, boolean shared) {
        double middle = (step.minDistance() + step.maxDistance()) / 2.0;
        for (Holder<Biome> biome : wanted) {
            if (!shared && taken.contains(key(biome))) {
                continue;
            }
            BlockPos spot = seen.getOrDefault(key(biome), List.of()).stream()
                .filter(pos -> within(pos, spawn, step.minDistance(), step.maxDistance() * reach)
                    && chosen.stream().noneMatch(other -> other.distSqr(pos) < (double) SPACING * SPACING))
                .sorted(Comparator.comparingDouble(pos -> Math.abs(distance(pos, spawn) - middle)))
                .limit(8).filter(pos -> dry(level, pos.getX(), pos.getZ())).findFirst().orElse(null);
            if (spot != null) {
                return new Site(spot, biome);
            }
        }
        return null;
    }

    /**
     * The middle of a dungeon's land: the first spot outward from the ring's start (at a seeded angle) where the whole
     * land lies deep in the realm, away from the other lands and dungeons, on ground (or over the void, for a void
     * arena); in the Overworld, on dry land.
     */
    private static BlockPos landSpot(ServerLevel level, Realm realm, RouteStep step, BlockPos spawn, List<RealmBiomeSource.Land> lands,
                                     List<BlockPos> chosen, int to, int rimNeeded, int salt) {
        double edge = RealmBiomeSource.LAND_RADIUS * 1.25 + 120;
        double offset = Math.floorMod(level.getSeed() ^ salt, 1000) / 1000.0;
        to = Math.min(SURVEY_MAX, to);
        // An Overworld land keeps clear of spawn (the camp stays in the land it was made in).
        int from = realm == Realm.OVERWORLD ? Math.max(step.minDistance(), (int) edge + 150) : step.minDistance();
        for (int radius = from; radius <= to; radius += LAND_STEP) {
            int points = Math.max(8, (int) (2 * Math.PI * radius / LAND_STEP));
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * (i + offset * points) / points;
                int x = spawn.getX() + (int) (Math.cos(angle) * radius);
                int z = spawn.getZ() + (int) (Math.sin(angle) * radius);
                if (!fits(level, realm, x, z) || lands.stream().anyMatch(land -> sq(land.x() - x) + sq(land.z() - z) < sq(LAND_SPACING))
                    || chosen.stream().anyMatch(other -> sq(other.getX() - x) + sq(other.getZ() - z) < sq(LAND_SPACING))) {
                    continue;
                }
                // Its middle fits; of its rim, most (a river or a realm's edge may cross a little of it: the land is
                // only drawn where its realm is).
                int rim = 0;
                for (int k = 0; k < 12 && rim + (12 - k) >= rimNeeded; k++) {
                    double a = Math.PI * 2 * k / 12;
                    rim += fits(level, realm, x + (int) (Math.cos(a) * edge), z + (int) (Math.sin(a) * edge)) ? 1 : 0;
                }
                boolean whole = rim >= rimNeeded;
                BlockPos middle = new BlockPos(x, spawn.getY(), z);
                if (whole && realm.hasGround(level, middle) != step.overVoid() && (realm != Realm.OVERWORLD || dry(level, x, z))) {
                    return middle;
                }
            }
        }
        return null;
    }

    /** Deep in the realm (no border blend); in the Overworld also dry land of its own climate (the terrain: see dry). */
    private static boolean fits(ServerLevel level, Realm realm, int x, int z) {
        RealmLayout.Sample sample = RealmLayout.sample(x, z);
        if (sample.first() != realm || sample.second() != realm) {
            return false;
        }
        return realm != Realm.OVERWORLD || !watery(surfaceBiome(level, x, z));
    }

    /** The land's middle and four points around it on ground above the sea, by the terrain (a few noise columns). */
    private static boolean dry(ServerLevel level, int x, int z) {
        int r = RealmBiomeSource.LAND_RADIUS / 3;
        return !wet(level, x, z) && !wet(level, x + r, z) && !wet(level, x - r, z) && !wet(level, x, z + r) && !wet(level, x, z - r);
    }

    /**
     * Water over the ground by the terrain itself: valleys and lakes below sea level (Tectonic cuts them) keep their
     * climate's biome - a desert's - while the ground there lies under water.
     */
    static boolean wet(ServerLevel level, int x, int z) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        // The sea floor two blocks or more under the sea level (one noise column; lakes above the sea are rare).
        return generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random)
            < generator.getSeaLevel() - 1;
    }

    /** Overworld biomes at the top of the world (lower down, under high land, the biome source answers caves), by spot. */
    private static Map<ResourceKey<Biome>, List<BlockPos>> survey(ServerLevel level, BlockPos spawn, List<RouteSteps.Entry> steps) {
        int radius = (int) Math.min(SURVEY_MAX, steps.stream().mapToInt(entry -> entry.step().maxDistance()).max().orElse(4000) * FAR_REACH);
        Map<ResourceKey<Biome>, List<BlockPos>> seen = new HashMap<>();
        for (int x = -radius; x <= radius; x += STEP) {
            for (int z = -radius; z <= radius; z += STEP) {
                if ((double) x * x + (double) z * z > (double) radius * radius) {
                    continue;
                }
                BlockPos pos = spawn.offset(x, 0, z);
                Realm realm = RealmWorld.realmAt(level, pos);
                if (realm != null && (realm != Realm.OVERWORLD || RealmLayout.sample(pos.getX(), pos.getZ()).second() != Realm.OVERWORLD)) {
                    continue; // a realm, or the Overworld's blended edge with one
                }
                surfaceBiome(level, pos.getX(), pos.getZ()).unwrapKey().ifPresent(key -> seen.computeIfAbsent(key, k -> new ArrayList<>()).add(pos));
            }
        }
        return seen;
    }

    private static Holder<Biome> surfaceBiome(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(level.getMaxBuildHeight() - 1),
            QuartPos.fromBlock(z), source.randomState().sampler());
    }

    /** The step's biome tag and exclusions (Mowzie's structures check their biome themselves). */
    private static boolean fitsTags(RouteStep step, Holder<Biome> biome) {
        return step.biomeTag().map(tag -> biome.is(TagKey.create(Registries.BIOME, tag))).orElse(true)
            && step.biomeExclude().stream().noneMatch(tag -> biome.is(TagKey.create(Registries.BIOME, tag)));
    }

    /** Water: an ocean, a river, a shore (no land of one is made on dry ground, nor on water). */
    private static boolean watery(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
            || biome.unwrapKey().map(key -> key.location().getPath().contains("shore")).orElse(false);
    }

    private static ResourceKey<Biome> key(Holder<Biome> biome) {
        return biome.unwrapKey().orElseThrow();
    }

    private static boolean within(BlockPos pos, BlockPos spawn, double from, double to) {
        double d = distance(pos, spawn);
        return d >= from && d <= to;
    }

    private static double distance(BlockPos pos, BlockPos spawn) {
        return Math.sqrt(sq(pos.getX() - spawn.getX()) + sq(pos.getZ() - spawn.getZ()));
    }

    private static double sq(double v) {
        return v * v;
    }
}
