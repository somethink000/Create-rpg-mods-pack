package net.expedition.world.realm;

import java.util.List;
import net.minecraft.util.Mth;

/**
 * The world's map, as an old chart of a flat world: a round world of continents ({@link #RADIUS} blocks across from the
 * middle, open sea beyond its last coasts) on a square sea of the same half-width (the world border; beyond it a ridge
 * of ice, see RealmTerrain). Its continents lie in rings round the middle one, the way the story
 * goes: the home continent (chapter I), then a ring of continents for each chapter after, each continent a ragged shape
 * of 2-4 km (Voronoi of jittered points on the rings, warped by a smooth noise) with a strait of sea between it and its
 * neighbours ({@link #STRAIT} blocks to either side) and open sea along the rim ({@link #RIM_SEA}). Every continent is
 * one realm: the realms a chapter's dungeons stand in lie in its ring (RING_REALMS), the rest is mostly the Overworld.
 * A realm's continent holds several of its biomes, one per region of about {@link #REGION} blocks; it fades into the
 * Overworld's sea over {@link #BLEND} blocks towards its coast. The land itself (continentalness) is shaped by
 * Continents. Everything comes from the world seed.
 */
public final class RealmLayout {
    private RealmLayout() {
    }

    /** A continent's size, roughly (rings this far apart). */
    static final int CELL = 2000;
    /** A realm's region inside its continent: one of its biomes each. */
    static final int REGION = 1400;
    static final int BLEND = 96;
    /**
     * The world's half-width: the round world's radius (its continents end before it, open sea beyond, out to the
     * corners) and the square border's distance from the middle (the ice ridge right behind it). RIM_SEA: open sea
     * inside the round world's edge.
     */
    public static final int RADIUS = 15000;
    public static final int RIM_SEA = 500;
    /** Half the width of the sea between two continents. */
    public static final int STRAIT = 160;
    private static final int WARP = 420;
    private static final int WARP_WAVE = 1100;
    /** Around spawn this far: always the Overworld's dry land (a strait passing near bends round it). */
    private static final int SPAWN_CORE = 1700;
    private static final int SPAWN_SHORE = 300;

    // One layout per JVM: Minecraft runs one server at a time (dedicated, or the integrated one whose client shares
    // these fields); it is set before that server's levels are created, and clients get it by RealmLayoutPayload.
    private static volatile long seed;
    private static volatile List<Realm> available = List.of(Realm.OVERWORLD);

    private static volatile boolean known;
    /** Bumped on every (re)configuration: caches of layout-dependent values check it. */
    static volatile int generation;

    /** Server: the layout key is derived from the world seed (clients get the key, never the seed). */
    /** The layout's centre: dry land near (0, 0), where spawn goes (cells, the spawn disc and the realm ring centre here). */
    private static volatile int originX;
    private static volatile int originZ;

    static void configure(long worldSeed, List<Realm> realms, int centreX, int centreZ) {
        long key = worldSeed * 0x9E3779B97F4A7C15L + 0x5851F42D4C957F2DL;
        configureKey(key ^ (key >>> 29), realms, centreX, centreZ);
    }

    public static void configureKey(long key, List<Realm> realms) {
        configureKey(key, realms, 0, 0);
    }

    /** Client (from the server's {@code RealmLayoutPayload}), or the server itself. */
    public static void configureKey(long key, List<Realm> realms, int centreX, int centreZ) {
        seed = key;
        generation++;
        available = List.copyOf(realms);
        originX = centreX;
        originZ = centreZ;
        known = true;
    }

    public static int originX() {
        return originX;
    }

    public static int originZ() {
        return originZ;
    }

    public static long key() {
        return seed;
    }

    public static List<Realm> realms() {
        return available;
    }

    /** True once the layout of the current world is known on this side. */
    public static boolean known() {
        return known;
    }

    /**
     * The realm at a point and the one it blends into (the Overworld's sea towards a coast), the weight of the first
     * (0.5..1; first == second deep inside a continent); cell: a hash of the continent (which of its realm's biomes it
     * holds); edge: blocks to the continent's border (the middle of the strait); radius: blocks from the middle (the
     * round world: its continents end towards RADIUS); square: the larger of x and z from the middle (RADIUS on the world
     * border, the ice ridge behind it); rim: blocks inside the round world's ragged edge (where its last coasts end,
     * negative beyond it).
     */
    public record Sample(Realm first, Realm second, double weight, Realm near, long cell, double edge, double radius, double square, double rim) {
        public double weightOf(Realm realm) {
            return (first == realm ? weight : 0) + (second == realm ? 1 - weight : 0);
        }
    }

    private static final ThreadLocal<long[]> LAST_KEY = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private static final ThreadLocal<Sample[]> LAST = ThreadLocal.withInitial(() -> new Sample[1]);

    public static Sample sample(int blockX, int blockZ) {
        // Every 4 blocks: borders blend over ~100 blocks, and a chunk reads 16 samples instead of 256.
        int x = blockX & ~3;
        int z = blockZ & ~3;
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ seed * 31 ^ ((long) originX << 20) ^ originZ;
        long[] lastKey = LAST_KEY.get();
        Sample[] last = LAST.get();
        if (lastKey[0] == key && last[0] != null) {
            return last[0];
        }
        Sample result = compute(x, z);
        lastKey[0] = key;
        last[0] = result;
        return result;
    }

    public static Realm dominant(int x, int z) {
        return sample(x, z).first();
    }

    private static Sample compute(int worldX, int worldZ) {
        int x = worldX - originX;
        int z = worldZ - originZ;
        double wx = x + WARP * (noise(x, z, 11, WARP_WAVE) * 2 - 1);
        double wz = z + WARP * (noise(x, z, 23, WARP_WAVE) * 2 - 1);
        Plate[] plates = plates();
        double best = Double.MAX_VALUE;
        double secondBest = Double.MAX_VALUE;
        Plate nearest = plates[0];
        // Plates of one landmass have no sea between them: the coast is the bisector to the nearest plate of another.
        double[] distances = DISTANCES.get();
        int[] found = FOUND.get();
        int count = 0;
        int bx = Math.floorDiv((int) wx, BUCKET);
        int bz = Math.floorDiv((int) wz, BUCKET);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] bucket = BUCKETS.get(((long) (bx + dx) << 32) ^ ((bz + dz) & 0xFFFFFFFFL));
                if (bucket == null) {
                    continue;
                }
                for (int index : bucket) {
                    Plate plate = plates[index];
                    double d = Math.sqrt((wx - plate.x) * (wx - plate.x) + (wz - plate.z) * (wz - plate.z));
                    if (count < found.length) {
                        found[count] = index;
                        distances[count++] = d;
                    }
                    if (d < best) {
                        best = d;
                        nearest = plate;
                    }
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (plates[found[i]].landmass != nearest.landmass && distances[i] < secondBest) {
                secondBest = distances[i];
            }
        }
        Realm first = nearest.realm;
        long firstCell = region(x, z) ^ nearest.id * 0x9E3779B97F4A7C15L;
        // Distance to the bisector between the two nearest points is ~(d2 - d1) / 2: the continent's border.
        double edge = (secondBest - best) / 2;
        double radius = Math.sqrt((double) x * x + (double) z * z);
        double square = Math.max(Math.abs((double) x), Math.abs((double) z));
        double core = SPAWN_CORE - radius;
        if (core > 0) {
            first = Realm.OVERWORLD;
            edge = Math.max(edge, core + STRAIT + SPAWN_SHORE);
        }
        // The round world's edge is a coast like any other: its line wanders (bays, capes), never nearer the border than RIM_SEA.
        double rim = rimLine(x, z) - radius;
        double coast = Math.min(edge - STRAIT, rim);
        if (first == Realm.OVERWORLD || coast >= BLEND) {
            Realm only = coast <= 0 ? Realm.OVERWORLD : first;
            Realm near = coast < BLEND + 16 ? Realm.OVERWORLD : first;
            return new Sample(only, only, 1, only == Realm.OVERWORLD && first != Realm.OVERWORLD ? first : near, firstCell, edge, radius, square, rim);
        }
        // A realm towards its coast: into the Overworld's sea.
        if (coast <= 0) {
            // The strait itself: the Overworld's sea alone (the realm only near, for its terrain's gates).
            return new Sample(Realm.OVERWORLD, Realm.OVERWORLD, 1, first, firstCell, edge, radius, square, rim);
        }
        double w = Mth.smoothstep(coast / BLEND);
        if (w >= 0.5) {
            return new Sample(first, Realm.OVERWORLD, w, Realm.OVERWORLD, firstCell, edge, radius, square, rim);
        }
        return new Sample(Realm.OVERWORLD, first, 1 - w, first, firstCell, edge, radius, square, rim);
    }

    /** The realms within a chunk and a margin around it (border blends and neighbours' aquifers reach that far). */
    public static java.util.Set<Realm> realmsAround(int chunkMinX, int chunkMinZ) {
        java.util.Set<Realm> realms = java.util.EnumSet.noneOf(Realm.class);
        for (int dx = -32; dx <= 48; dx += 8) {
            for (int dz = -32; dz <= 48; dz += 8) {
                Sample sample = sample(chunkMinX + dx, chunkMinZ + dz);
                realms.add(sample.first());
                realms.add(sample.second());
                realms.add(sample.near());
            }
        }
        return realms;
    }

    /** A continent: its point (Voronoi seed), its realm, its ring (0: the home continent) and its number. */
    private record Plate(double x, double z, Realm realm, int ring, int id, int landmass) {
    }

    private static final ThreadLocal<double[]> DISTANCES = ThreadLocal.withInitial(() -> new double[64]);
    private static final ThreadLocal<int[]> FOUND = ThreadLocal.withInitial(() -> new int[64]);
    /** Neighbouring continents of one realm grow into one landmass this often (no strait between them). */
    private static final double MERGE = 0.75;
    /** Plates nearer than this are neighbours. */
    private static final double NEIGHBOUR = 3600;

    /**
     * The realms a ring's continents must hold (the chapter's dungeons stand in them): ring 1 is chapter II, ring 2
     * chapter III... Each once, the rest of the ring the Overworld (and now and then another realm, for the wild).
     */
    private static final List<List<Realm>> RING_REALMS = List.of(
        List.of(),
        List.of(Realm.NETHER, Realm.TWILIGHT),
        List.of(Realm.NETHER, Realm.END, Realm.TWILIGHT, Realm.AETHER),
        List.of(Realm.NETHER, Realm.END, Realm.TWILIGHT, Realm.AETHER),
        List.of(Realm.NETHER, Realm.END, Realm.TWILIGHT, Realm.AETHER, Realm.OTHERSIDE),
        List.of(Realm.NETHER, Realm.END, Realm.TWILIGHT, Realm.OTHERSIDE),
        List.of(Realm.END, Realm.NETHER));
    /** Rings: their distance from the middle and how many continents each holds (the outer ones fill the square's corners). */
    private static final int[] RING_RADIUS = {0, 2600, 4600, 6600, 8600, 10600, 12600, 14600};
    private static final int[] RING_COUNT = {1, 6, 10, 13, 16, 19, 22, 25};
    /** Chance a free place of a ring is another realm instead of the Overworld. */
    private static final double WILD_REALM = 0.2;
    /** The round world's ragged edge: its radius wanders between these, slowly round it. */
    private static final int RIM_INNER = RADIUS - RIM_SEA - 1900;
    private static final int RIM_WANDER = 1900;

    /** The radius of the round world's edge in the direction of a point. */
    static double rimLine(int x, int z) {
        return RIM_INNER + RIM_WANDER * (0.65 * noise(x, z, 61, 2600) + 0.35 * noise(x, z, 67, 900));
    }

    /** Bucket grid for finding the nearest continents. */
    private static final int BUCKET = 4096;

    private static volatile Plate[] platesFor;
    private static volatile int platesGeneration = -1;
    private static final java.util.Map<Long, int[]> BUCKETS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Plate[] plates() {
        if (platesGeneration == generation && platesFor != null) {
            return platesFor;
        }
        synchronized (RealmLayout.class) {
            if (platesGeneration == generation && platesFor != null) {
                return platesFor;
            }
            java.util.Random random = new java.util.Random(seed ^ 0x9A7E5L);
            List<Plate> plates = new java.util.ArrayList<>();
            plates.add(new Plate(0, 0, Realm.OVERWORLD, 0, 0, 0));
            for (int ring = 1; ring < RING_RADIUS.length; ring++) {
                int count = RING_COUNT[ring];
                List<Realm> slots = new java.util.ArrayList<>();
                if (ring < RING_REALMS.size()) {
                    RING_REALMS.get(ring).stream().filter(available::contains).forEach(slots::add);
                }
                List<Realm> wild = available.stream().filter(realm -> realm != Realm.OVERWORLD).toList();
                while (slots.size() < count) {
                    slots.add(!wild.isEmpty() && random.nextDouble() < WILD_REALM ? wild.get(random.nextInt(wild.size())) : Realm.OVERWORLD);
                }
                java.util.Collections.shuffle(slots, random);
                double turn = random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < count; i++) {
                    double angle = turn + (i + (random.nextDouble() - 0.5) * 0.5) * Math.PI * 2 / count;
                    double radius = RING_RADIUS[ring] + (random.nextDouble() - 0.5) * 900;
                    plates.add(new Plate(Math.cos(angle) * radius, Math.sin(angle) * radius, slots.get(i), ring, plates.size(), plates.size()));
                }
            }
            // Landmasses: neighbours of one realm merge (union-find), the home continent never with a realm's.
            int[] parent = new int[plates.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }
            for (int a = 0; a < plates.size(); a++) {
                for (int b = a + 1; b < plates.size(); b++) {
                    Plate pa = plates.get(a);
                    Plate pb = plates.get(b);
                    if (pa.realm == pb.realm && Math.hypot(pa.x - pb.x, pa.z - pb.z) < NEIGHBOUR && random.nextDouble() < MERGE) {
                        parent[find(parent, a)] = find(parent, b);
                    }
                }
            }
            for (int i = 0; i < plates.size(); i++) {
                Plate plate = plates.get(i);
                plates.set(i, new Plate(plate.x, plate.z, plate.realm, plate.ring, plate.id, find(parent, i)));
            }
            BUCKETS.clear();
            java.util.Map<Long, List<Integer>> buckets = new java.util.HashMap<>();
            for (Plate plate : plates) {
                long key = ((long) Math.floorDiv((int) plate.x, BUCKET) << 32) ^ (Math.floorDiv((int) plate.z, BUCKET) & 0xFFFFFFFFL);
                buckets.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(plate.id);
            }
            buckets.forEach((key, list) -> BUCKETS.put(key, list.stream().mapToInt(Integer::intValue).toArray()));
            platesFor = plates.toArray(Plate[]::new);
            platesGeneration = generation;
            return platesFor;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** The region of a continent at a point (jittered cells of REGION, warped on their own): which of its realm's biomes. */
    private static long region(int x, int z) {
        double wx = x + 300 * (noise(x, z, 31, 700) * 2 - 1);
        double wz = z + 300 * (noise(x, z, 37, 700) * 2 - 1);
        int cx = Math.floorDiv((int) wx, REGION);
        int cz = Math.floorDiv((int) wz, REGION);
        double best = Double.MAX_VALUE;
        long result = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = hash(cx + dx, cz + dz, 5);
                double px = (cx + dx + 0.15 + 0.7 * ((h & 0xFFFF) / 65536.0)) * REGION;
                double pz = (cz + dz + 0.15 + 0.7 * ((h >>> 16 & 0xFFFF) / 65536.0)) * REGION;
                double d = (wx - px) * (wx - px) + (wz - pz) * (wz - pz);
                if (d < best) {
                    best = d;
                    result = hash(cx + dx, cz + dz, 3);
                }
            }
        }
        return result;
    }

    /** Smooth value noise in [0, 1] with the given wavelength (blocks). */
    static double noise(int x, int z, int salt, int wave) {
        double fx = (double) x / wave;
        double fz = (double) z / wave;
        int ix = Mth.floor(fx);
        int iz = Mth.floor(fz);
        double tx = Mth.smoothstep(fx - ix);
        double tz = Mth.smoothstep(fz - iz);
        double a = unit(hash(ix, iz, salt));
        double b = unit(hash(ix + 1, iz, salt));
        double c = unit(hash(ix, iz + 1, salt));
        double d = unit(hash(ix + 1, iz + 1, salt));
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    static long hash(int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    /** The world border: the square's sides (the open sea runs up to it, the ice ridge rises right behind it, out of reach). */
    @net.neoforged.fml.common.EventBusSubscriber(modid = net.expedition.Expedition.MOD_ID)
    static final class Border {
        private Border() {
        }


        @net.neoforged.bus.api.SubscribeEvent
        static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
            var overworld = event.getServer().overworld();
            if (!(overworld.getChunkSource().getGenerator().getBiomeSource() instanceof RealmBiomeSource)) {
                return;
            }
            var border = overworld.getWorldBorder();
            border.setCenter(originX + 0.5, originZ + 0.5);
            border.setSize(2.0 * RADIUS);
            net.expedition.Expedition.LOG.info("World border: {} blocks round {}, {}", (int) border.getSize(), originX, originZ);
        }
    }
}
