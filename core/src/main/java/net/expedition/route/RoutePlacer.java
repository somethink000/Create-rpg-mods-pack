package net.expedition.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.expedition.Expedition;
import net.minecraft.Util;
import net.expedition.garrison.Garrisons;
import net.expedition.world.StructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Places a route dungeon far from the player without freezing the server: picks a spot in the distance ring, lays the
 * structure out, loads its chunks in the background with tickets, checks the area holds no other structure, then
 * builds it. Structures that can't generate at a spot (some need to be underground) and occupied spots are retried.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class RoutePlacer {
    private RoutePlacer() {
    }

    public record Result(String dimension, BlockPos pos, String dungeonKey) {
    }

    static final int MAX_ATTEMPTS = 16;
    /** Attempts that require the structure's own biomes (an ocean monument in an ocean); after that any biome. */
    static final int BIOME_ATTEMPTS = 8;
    /** Rock over an underground dungeon's top, at most (all but the last attempts look for lower land over it). */
    static final int MAX_BURIED = 50;
    /** Ring points sampled per attempt for a fitting surface biome. */
    static final int BIOME_SAMPLES = 64;
    /** For a realm's dungeon or a biome-tag step: a realm or biome covers a small share of the ring. */
    static final int NARROW_SAMPLES = 400;
    static final int LOAD_TIMEOUT_TICKS = 20 * 60;
    static final int CHUNKS_PER_TICK = 4;
    private static final int TICKET_DISTANCE = 1;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("expedition_route", Comparator.comparingLong(ChunkPos::toLong));

    private static final class Job {
        final ServerLevel level;
        final List<ResourceLocation> structures;
        final BlockPos center;
        final int minDistance;
        int maxDistance;
        final Consumer<Result> done;
        /** A merged realm's dungeon: only that realm's biomes, never the any-biome fallback; the ring widens instead. */
        boolean strict;
        /** For a biome-tag step: the nearest point of that biome beyond the minimum distance, found once by a ring scan. */
        BlockPos tagAnchor;
        /** Biome tags the spot must not have (with biomeTag). */
        List<net.minecraft.tags.TagKey<Biome>> biomeExclude = List.of();
        boolean tagScanned;
        /** Over the void instead of on an island (see RouteStep.overVoid). */
        boolean overVoid;
        /** In the realm world: the realm the dungeon must stand in (the step's former dimension, else the Overworld). */
        net.expedition.world.realm.Realm realm;
        int attempts;
        Structure structure;
        StructureStart start;
        List<ChunkPos> ticketed = List.of();
        int waited;
        /** Chunks still to build once the start is registered; null until the spot is accepted. */
        List<ChunkPos> toBuild;
        /** Structure layout running off the main thread. */
        CompletableFuture<StructureStart> layout;
        boolean layoutCheckedBiome;
        net.minecraft.tags.TagKey<Biome> biomeTag;
        /** The step's own biome (HomeBiomes): only spots of it, around its land (tagAnchor); null when it has none. */
        Holder<Biome> homeBiome;
        /** Without a land of its own (HomeBiomes found none for this world): any of the step's home biomes will do. */
        List<net.minecraft.resources.ResourceKey<Biome>> homeAny = List.of();
        /** Why attempts failed, for the log. */
        final java.util.Map<String, Integer> reasons = new java.util.TreeMap<>();

        Job(ServerLevel level, List<ResourceLocation> structures, BlockPos center, int minDistance, int maxDistance, Consumer<Result> done) {
            this.level = level;
            this.structures = structures;
            this.center = center;
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.done = done;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();
    /** Requests made while jobs are being stepped (a finished job's callback may ask for the next placement). */
    private static final List<Job> INCOMING = new ArrayList<>();

    /** Starts placing one of {@code structures} {@code minDistance..maxDistance} blocks from {@code center}. */
    public static void request(ServerLevel level, List<ResourceLocation> structures, BlockPos center, int minDistance, int maxDistance,
                               Consumer<Result> done) {
        request(level, structures, center, minDistance, maxDistance, java.util.Optional.empty(), done);
    }

    /** biomeTag: only spots in this biome tag (every attempt), for structures that check their biome themselves. */
    public static void request(ServerLevel level, List<ResourceLocation> structures, BlockPos center, int minDistance, int maxDistance,
                               java.util.Optional<ResourceLocation> biomeTag, Consumer<Result> done) {
        request(level, structures, center, minDistance, maxDistance, biomeTag, null, false, done);
    }

    /** realm: in the realm world, the realm the dungeon must stand in (a merged dimension's realm is strict). */
    public static void request(ServerLevel level, List<ResourceLocation> structures, BlockPos center, int minDistance, int maxDistance,
                               java.util.Optional<ResourceLocation> biomeTag, net.expedition.world.realm.Realm realm, boolean overVoid,
                               Consumer<Result> done) {
        // A biome restriction can leave the tier's ring empty (no savanna there): such steps may look twice as far.
        Job job = new Job(level, structures, center, minDistance, biomeTag.isPresent() ? maxDistance * 2 : maxDistance, done);
        job.biomeTag = biomeTag.map(id -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id)).orElse(null);
        job.realm = realm;
        job.overVoid = overVoid;
        job.strict = realm != null && realm != net.expedition.world.realm.Realm.OVERWORLD;
        INCOMING.add(job);
    }

    /** A route step's dungeon: its structures, ring, biome tag and exclusions, realm and void flag. */
    public static void request(ServerLevel level, RouteStep step, BlockPos center, net.expedition.world.realm.Realm realm, Consumer<Result> done) {
        request(level, step, center, realm, 0, done);
    }

    /** As above, on a story branch (its lands: HomeBiomes; 0 is the spawn branch). */
    public static void request(ServerLevel level, RouteStep step, BlockPos center, net.expedition.world.realm.Realm realm, int branch, Consumer<Result> done) {
        request(level, step.structures(), center, step.minDistance(), step.maxDistance(), step.biomeTag(), realm, step.overVoid(), done);
        Job job = INCOMING.get(INCOMING.size() - 1);
        job.biomeExclude = step.biomeExclude().stream()
            .map(id -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id)).toList();
        if (level == level.getServer().overworld()) {
            RouteSteps.all().stream().filter(entry -> entry.step() == step).findFirst()
                .map(entry -> HomeBiomes.of(level, branch, entry.id().getPath())).ifPresent(site -> {
                    // Its own land: placed there (a realm dungeon in the land's middle), however far that is.
                    job.homeBiome = site.biome();
                    job.tagAnchor = site.pos();
                    job.tagScanned = true;
                    job.maxDistance = Math.max(job.maxDistance, (int) Math.sqrt(site.pos().distSqr(center.atY(site.pos().getY()))) + 512);
                });
            if (job.homeBiome == null && job.biomeTag == null) {
                // No land of its own in this world: still one of its lands (a pyramid in a desert, not a spruce wood).
                job.homeAny = step.homeBiomes().stream().map(id -> net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, id)).toList();
            }
        }
    }

    public static boolean busy() {
        return !JOBS.isEmpty() || !INCOMING.isEmpty();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        JOBS.addAll(INCOMING);
        INCOMING.clear();
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (step(job)) {
                iterator.remove();
            }
        }
    }

    /** Chunks held by tickets never unload, and a stopping server waits for every chunk to unload: drop all jobs. */
    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        JOBS.addAll(INCOMING);
        INCOMING.clear();
        JOBS.forEach(RoutePlacer::releaseTickets);
        JOBS.clear();
    }

    /** Advances a job by one tick; true when finished (placed or given up). */
    static boolean step(Job job) {
        if (job.layout != null) {
            if (!job.layout.isDone()) {
                return false;
            }
            acceptLayout(job);
            return false;
        }
        if (job.start == null) {
            if (job.attempts >= MAX_ATTEMPTS) {
                Expedition.LOG.warn("Route: could not place any of {} near {}, failures {}", job.structures, job.center, job.reasons);
                job.done.accept(null);
                return true;
            }
            if ((job.homeBiome != null || !job.homeAny.isEmpty()) && job.attempts == MAX_ATTEMPTS / 2) {
                // Its land took no layout after many tries: placed as without one rather than not at all.
                Expedition.LOG.warn("Route: {} would not stand in {}, placed as before", job.structures,
                    job.homeBiome != null ? job.homeBiome.getRegisteredName() : job.homeAny);
                job.homeBiome = null;
                job.homeAny = List.of();
                job.tagAnchor = null;
                job.tagScanned = false;
            }
            job.attempts++;
            plan(job);
            return false;
        }
        if (!loaded(job)) {
            if (++job.waited > LOAD_TIMEOUT_TICKS) {
                job.reasons.merge("chunks did not load", 1, Integer::sum);
                release(job);
            }
            return false;
        }
        if (job.toBuild != null) {
            for (int i = 0; i < CHUNKS_PER_TICK && !job.toBuild.isEmpty(); i++) {
                StructurePlacer.buildChunk(job.level, job.start, job.toBuild.removeLast());
            }
            if (!job.toBuild.isEmpty()) {
                return false;
            }
            return finish(job);
        }
        String occupant = occupied(job);
        if (occupant != null) {
            job.reasons.merge("area occupied by " + occupant, 1, Integer::sum);
            release(job);
            return false;
        }
        // Register now (lookups see it immediately), build a few chunks per tick so a big dungeon doesn't stall the server.
        StructurePlacer.register(job.level, job.structure, job.start);
        job.toBuild = new ArrayList<>(job.ticketed);
        return false;
    }

    private static boolean finish(Job job) {
        String id = Garrisons.structureId(job.level, job.structure);
        ChunkPos chunk = job.start.getChunkPos();
        BlockPos pos = job.start.getBoundingBox().getCenter();
        releaseTickets(job);
        Expedition.LOG.info("Route: placed {} at {} after {} attempt(s), failures {}; seat: box y {}..{}, ground around {}", id, pos.toShortString(),
            job.attempts, job.reasons, job.start.getBoundingBox().minY(), job.start.getBoundingBox().maxY(), net.expedition.world.StructurePlacer.groundAround(job.level, job.start.getBoundingBox()));
        job.done.accept(new Result(job.level.dimension().location().toString(), pos, id + "|" + chunk.x + "," + chunk.z));
        return true;
    }

    private static void plan(Job job) {
        ResourceLocation id = job.structures.get(job.level.random.nextInt(job.structures.size()));
        Structure structure = job.level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        if (structure == null) {
            Expedition.LOG.error("Route: unknown structure {}", id);
            job.reasons.merge("unknown structure", 1, Integer::sum);
            return;
        }
        boolean checkBiome = job.strict || job.attempts <= BIOME_ATTEMPTS;
        BlockPos origin = null;
        // Cheap biome samples first (no chunks, no structure layout): only a fitting spot gets the costly layout.
        int samples = job.strict || targeted(job) ? NARROW_SAMPLES : BIOME_SAMPLES;
        for (int sample = 0; sample < samples && origin == null; sample++) {
            BlockPos point = targeted(job) && nearestTagged(job) != null ? aroundAnchor(job) : ringPoint(job);
            // Structures lay out from their chunk's middle: check that very column (ground under an End island).
            BlockPos candidate = new net.minecraft.world.level.ChunkPos(point).getMiddleBlockPosition(point.getY());
            if (job.realm != null && net.expedition.world.realm.RealmWorld.realmAt(job.level, candidate) != job.realm) {
                continue;
            }
            if (job.realm != null && job.realm.hasGround(job.level, candidate) == job.overVoid) {
                continue; // an End or Aether point over the void (or, for a void arena, over land)
            }
            if (!job.overVoid && !aquatic(job) && water(job.level, candidate.getX(), candidate.getZ())) {
                job.reasons.merge("spot in water", 1, Integer::sum);
                continue; // a dry dungeon's spot in a river, lake or the sea
            }
            if (marine(job) && job.attempts <= MAX_ATTEMPTS - 2 && !water(job.level, candidate.getX(), candidate.getZ())) {
                job.reasons.merge("spot on land", 1, Integer::sum);
                continue; // a sunken city or a ship on dry land: its boss swims
            }
            Holder<Biome> biome = !checkBiome && !targeted(job) ? null
                : targeted(job) ? groundBiome(job.level, candidate) : surfaceBiome(job.level, candidate);
            // A realm's dungeon may stand in any biome of its realm (the realm check above): each realm cell is a single
            // biome, and the one biome a dungeon lists (warped forest) can lie tens of kilometres away.
            // A home biome was chosen for the step on purpose: the structure stands in it even if it doesn't list it.
            if ((!checkBiome || job.strict || job.homeBiome != null || !job.homeAny.isEmpty() || structure.biomes().contains(biome)) && (!targeted(job) || fits(job, biome))) {
                origin = candidate;
            }
        }
        if (origin == null) {
            job.reasons.merge("no fitting biome", 1, Integer::sum);
            if (job.strict || targeted(job)) {
                job.maxDistance = job.maxDistance * 13 / 10; // the realm or biome may lie a bit beyond the tier's ring
            }
            return;
        }
        BlockPos layoutOrigin = origin;
        job.structure = structure;
        job.layoutCheckedBiome = checkBiome && job.homeBiome == null && job.homeAny.isEmpty();
        // Layout loads templates from disk and assembles jigsaw pieces: seconds for big dungeons. Vanilla does it on
        // worldgen threads, so it is safe off the main thread; the result is picked up on a later tick.
        job.layout = CompletableFuture.supplyAsync(() -> StructurePlacer.plan(job.level, structure, layoutOrigin, !checkBiome || job.strict || job.homeBiome != null || !job.homeAny.isEmpty()), Util.backgroundExecutor());
    }

    private static void acceptLayout(Job job) {
        StructureStart start;
        try {
            start = job.layout.getNow(null);
        } catch (RuntimeException error) {
            Expedition.LOG.warn("Route: layout of {} failed: {}", job.structure, error.toString());
            start = null;
        }
        job.layout = null;
        if (start == null) {
            job.reasons.merge(job.layoutCheckedBiome ? "layout failed (biome at structure height?)" : "layout failed", 1, Integer::sum);
            job.structure = null;
            return;
        }
        // An underground dungeon under a mountain (tens of blocks of rock over it) or under a lake or the sea (its
        // staircase would come out in the water): another spot, while there are attempts to spare.
        BoundingBox frame = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()).orElse(null);
        if (frame != null) {
            int ground = net.expedition.world.StructurePlacer.groundAround(job.level, frame);
            var center = frame.getCenter();
            // A river, lake or sea across a third of it or more, over it or around it, on every try (a dungeon of the
            // sea or the shore is meant to stand in water; a sky or void dungeon has none under it).
            boolean underWater = !job.overVoid && !aquatic(job) && waterShare(job.level, frame) >= 3;
            // A big dungeon laid out from a corner chunk may reach over a realm's edge: its middle must be in its realm.
            boolean outside = job.realm != null && net.expedition.world.realm.RealmWorld.realmAt(job.level, center) != job.realm;
            boolean buried = job.attempts <= MAX_ATTEMPTS - 2 && ground - frame.maxY() > MAX_BURIED;
            // A dungeon of the open sea mostly over water (the last tries take what they get rather than none).
            boolean ashore = marine(job) && job.attempts <= MAX_ATTEMPTS - 2 && waterShare(job.level, frame) < 5;
            if (underWater || outside || buried || ashore) {
                job.reasons.merge(underWater ? "under water" : outside ? "outside its realm" : buried ? "buried too deep" : "ashore", 1, Integer::sum);
                job.structure = null;
                return;
            }
        }
        job.start = start;
        job.waited = 0;
        job.ticketed = new ArrayList<>();
        ChunkPos.rangeClosed(StructurePlacer.minChunk(start), StructurePlacer.maxChunk(start)).forEach(pos -> {
            job.level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            job.ticketed.add(pos);
        });
    }

    /** Is there water (two blocks deep or more) over the ground here, by the generator? */
    private static boolean water(ServerLevel level, int x, int z) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        // The sea floor two blocks or more under the sea level (one noise column; lakes above the sea are rare).
        return generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random)
            < generator.getSeaLevel() - 1;
    }

    /** Of 9 points over the frame (a 3x3 grid, a little inside it), how many stand in water. */
    public static int waterShare(ServerLevel level, BoundingBox frame) {
        int count = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int x = frame.minX() + (frame.getXSpan() - 1) * (1 + 2 * i) / 6;
                int z = frame.minZ() + (frame.getZSpan() - 1) * (1 + 2 * j) / 6;
                if (water(level, x, z)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** A dungeon of the sea, a river or the shore (its home biome or tag, or its structure), or of the Nether: may stand over fluid. */
    private static boolean aquatic(Job job) {
        if (job.realm == net.expedition.world.realm.Realm.NETHER) {
            return true; // the heightmaps count its lava seas as water: Nether dungeons stand over them
        }
        if (job.homeBiome != null && (job.homeBiome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || job.homeBiome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
            || job.homeBiome.is(net.minecraft.tags.BiomeTags.IS_BEACH))) {
            return true;
        }
        String names = (job.biomeTag == null ? "" : job.biomeTag.location().getPath()) + " " + job.homeAny + " " + job.structures;
        return java.util.regex.Pattern.compile("ocean|river|beach|sea|ship|sunken|monument|acropolis|kraken|coral|underwater").matcher(names).find();
    }

    /** A dungeon of the open sea (its home is the ocean, or it is a ship or a sunken place): it must stand in water. */
    private static boolean marine(Job job) {
        if (job.overVoid || job.realm == net.expedition.world.realm.Realm.NETHER) {
            return false;
        }
        if (job.homeBiome != null && job.homeBiome.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) {
            return true;
        }
        return java.util.regex.Pattern.compile("sunken|acropolis|monument|kraken_ship|underwater|pirate_ship").matcher(job.structures + " " + job.homeAny).find();
    }

    /** Ring spacing of the biome-tag scan, and how far it looks at most. */
    static final int TAG_SCAN_STEP = 192;
    static final int TAG_SCAN_RADIUS = 10000; // no story dungeon further than about 10 km (HomeBiomes)

    /**
     * The nearest point of the step's biome tag (in its realm) beyond its minimum distance: rings outward, the surface
     * biome read straight from the biome source at the top of the world (microseconds a point). Biomes are kilometres
     * wide, so random ring points miss them; this finds the closest one. Null if none within the scan radius.
     */
    static BlockPos nearestTagged(Job job) {
        if (job.tagScanned) {
            return job.tagAnchor;
        }
        job.tagScanned = true;
        var source = job.level.getChunkSource();
        int quartY = QuartPos.fromBlock(job.level.getMaxBuildHeight() - 1);
        for (int radius = job.minDistance; radius <= TAG_SCAN_RADIUS; radius += TAG_SCAN_STEP) {
            int points = Math.max(8, (int) (2 * Math.PI * radius / TAG_SCAN_STEP));
            double offset = job.level.random.nextDouble();
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * (i + offset) / points;
                int x = job.center.getX() + (int) (Math.cos(angle) * radius);
                int z = job.center.getZ() + (int) (Math.sin(angle) * radius);
                BlockPos pos = new BlockPos(x, job.center.getY(), z);
                if (job.realm != null && net.expedition.world.realm.RealmWorld.realmAt(job.level, pos) != job.realm) {
                    continue;
                }
                if (fits(job, source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z),
                    source.randomState().sampler()))) {
                    job.tagAnchor = pos;
                    job.maxDistance = Math.max(job.maxDistance, radius + 512);
                    Expedition.LOG.info("Route: nearest {} at {} ({} blocks)", job.homeBiome != null ? job.homeBiome.getRegisteredName()
                        : job.biomeTag != null ? job.biomeTag.location() : job.homeAny, pos.toShortString(), radius);
                    return pos;
                }
            }
        }
        if (job.homeBiome != null) {
            // Its own biome is nowhere within reach after all: placed as without one.
            Expedition.LOG.info("Route: no {} within reach, placed as before", job.homeBiome.getRegisteredName());
            job.homeBiome = null;
            job.tagScanned = false;
            return job.biomeTag != null ? nearestTagged(job) : null;
        }
        if (!job.homeAny.isEmpty()) {
            Expedition.LOG.info("Route: none of {} within reach, placed as before", job.homeAny);
            job.homeAny = List.of();
        }
        return null;
    }

    /** The step wants a certain biome: its own (HomeBiomes) or its tag. */
    static boolean targeted(Job job) {
        return job.homeBiome != null || job.biomeTag != null || !job.homeAny.isEmpty();
    }

    static boolean fits(Job job, Holder<Biome> biome) {
        boolean wanted = job.homeBiome != null ? biome.is(job.homeBiome.unwrapKey().orElseThrow())
            : job.biomeTag != null ? biome.is(job.biomeTag) : job.homeAny.stream().anyMatch(biome::is);
        return wanted && job.biomeExclude.stream().noneMatch(biome::is);
    }

    /** A point within 160 blocks of the tag anchor at first, wider with each failed try (a few tries find a spot the structure accepts). */
    private static BlockPos aroundAnchor(Job job) {
        double angle = job.level.random.nextDouble() * Math.PI * 2;
        // Wider with every failed try (a structure may want flat ground): at last most of the step's land.
        double distance = job.level.random.nextDouble() * Math.min(360, 160 + 25 * job.attempts);
        return job.tagAnchor.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
    }

    /** The farthest a dungeon of the round world may stand from its middle: the land's outer edge. */
    static final int REACH_MAX = net.expedition.world.realm.RealmLayout.RADIUS - net.expedition.world.realm.RealmLayout.RIM_SEA - 400;

    private static BlockPos ringPoint(Job job) {
        double angle = job.level.random.nextDouble() * Math.PI * 2;
        // In the round world never past the continents' rim (the ring widens after failures, but not into the ice wall).
        int max = job.realm == null ? job.maxDistance : Math.min(job.maxDistance, REACH_MAX);
        double distance = Math.min(job.minDistance, max) + job.level.random.nextDouble() * Math.max(0, max - job.minDistance);
        return BlockPos.containing(job.center.getX() + Math.cos(angle) * distance, job.center.getY(), job.center.getZ() + Math.sin(angle) * distance);
    }

    /** Biome at the terrain's top (peaks and slopes only exist up there): computes a noise column, about a millisecond. */
    private static Holder<Biome> groundBiome(ServerLevel level, BlockPos pos) {
        var source = level.getChunkSource();
        int y = source.getGenerator().getBaseHeight(pos.getX(), pos.getZ(), net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
            level, source.randomState());
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(y),
            QuartPos.fromBlock(pos.getZ()), source.randomState().sampler());
    }

    /** Biome at sea level straight from the biome source: no chunk loading, microseconds. */
    private static Holder<Biome> surfaceBiome(ServerLevel level, BlockPos pos) {
        var source = level.getChunkSource();
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(level.getSeaLevel()),
            QuartPos.fromBlock(pos.getZ()), source.randomState().sampler());
    }

    private static boolean loaded(Job job) {
        for (ChunkPos pos : job.ticketed) {
            if (job.level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                return false;
            }
        }
        return true;
    }

    /** Spread-out or buried structures that may share space with a dungeon. */
    /** Structures a dungeon may cover: buried ones, wrecks, and small decor (log piles, dead trees, hollow trees, rocks). */
    private static final List<String> MINOR = List.of("mineshaft", "buried_treasure", "fossil", "ocean_ruin", "shipwreck", "ruined_portal",
        "hollow_tree", "log_pile", "dead_tree", "boulder", "rock");

    /**
     * Another real structure overlaps the planned box. In the last (any-biome) attempts any spot is accepted: an overlap
     * is better than a route step without its dungeon.
     */
    private static String occupied(Job job) {
        if (job.attempts > BIOME_ATTEMPTS) {
            return null;
        }
        BoundingBox box = job.start.getBoundingBox();
        var manager = job.level.structureManager();
        for (ChunkPos pos : job.ticketed) {
            LevelChunk chunk = job.level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                continue;
            }
            for (var entry : chunk.getAllReferences().entrySet()) {
                String id = Garrisons.structureId(job.level, entry.getKey());
                if (entry.getValue().isEmpty() || MINOR.stream().anyMatch(id::contains)) {
                    continue;
                }
                boolean[] hit = {false};
                manager.fillStartsForStructure(entry.getKey(), entry.getValue(), other -> hit[0] |= other.getBoundingBox().intersects(box));
                if (hit[0]) {
                    return id;
                }
            }
        }
        return null;
    }

    private static void releaseTickets(Job job) {
        job.ticketed.forEach(pos -> job.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos));
        job.ticketed = List.of();
    }

    /** Drops the current spot and lets the next tick try another. */
    private static void release(Job job) {
        releaseTickets(job);
        job.start = null;
        job.structure = null;
        job.toBuild = null;
    }
}
