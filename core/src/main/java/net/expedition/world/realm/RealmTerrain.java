package net.expedition.world.realm;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * One density function of the world's noise router, taken per realm from that realm's own generator and blended across
 * realm borders. With {@code terrain}, each realm's function only covers its generator's height range: below it the
 * ground is solid or void ({@link Realm#solidFloor}), above it air; heights are the realm's own, lifted by {@link Realm#shift}.
 * A realm without a function of its own (null) gets just that floor/air shape.
 */
public final class RealmTerrain implements DensityFunction {
    public static final MapCodec<RealmTerrain> CODEC = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("overworld")
        .xmap(RealmTerrain::overworldOnly, terrain -> terrain.children[0]);
    private static final KeyDispatchDataCodec<RealmTerrain> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);
    private static final Realm[] REALMS = Realm.values();

    private final DensityFunction[] children;
    private final int[] minY;
    private final int[] maxY;
    private final boolean terrain;

    /** Which router slot this is (final or initial density): separates their caches. */
    private final int kind;

    RealmTerrain(DensityFunction[] children, int[] minY, int[] maxY, boolean terrain, int kind) {
        this.kind = kind;
        this.children = children;
        this.minY = minY;
        this.maxY = maxY;
        this.terrain = terrain;
    }

    private static RealmTerrain overworldOnly(DensityFunction overworld) {
        DensityFunction[] children = new DensityFunction[REALMS.length];
        children[0] = overworld;
        return new RealmTerrain(children, new int[REALMS.length], new int[REALMS.length], false, 0);
    }

    @Override
    public double compute(FunctionContext context) {
        RealmLayout.Sample sample = RealmLayout.sample(context.blockX(), context.blockZ());
        // Behind the world border: a wall of icy rock (its biome, the Ice Wall) - sheer cliffs from the sea, crags, spires
        // and overhangs against the sky, out of reach.
        if (terrain && sample.square() >= RealmLayout.RADIUS) {
            int x = context.blockX(), y = context.blockY(), z = context.blockZ();
            double peaks = RIDGE_LOW + (RIDGE_HIGH - RIDGE_LOW)
                * (0.6 * RealmLayout.noise(x, z, 53, 110) + 0.3 * RealmLayout.noise(x, z, 59, 37) + 0.1 * RealmLayout.noise(x, z, 61, 11));
            double rise = Math.min(1, (sample.square() - RealmLayout.RADIUS) / 10.0);
            double top = RIDGE_FOOT + (peaks - RIDGE_FOOT) * Math.sqrt(rise);
            // Crags: the rock face juts and recedes with height (ledges, overhangs), more so higher up.
            double crag = CRAG * (RealmLayout.noise(x, y, 71, 19) + RealmLayout.noise(z, y, 73, 19) - 1) * Math.min(1, Math.max(0, (y - 70) / 60.0));
            return y < top + crag ? 1 : -1;
        }
        double value = value(sample.first(), context);
        if (sample.weight() >= 1) {
            return value;
        }
        value = value * sample.weight() + value(sample.second(), context) * (1 - sample.weight());
        if (terrain) {
            // An open realm with its own sea (Nether lava, Otherside grime) gets a low rim along its border: the
            // neighbour's water never touches its lava.
            Realm closed = sample.first().rimTop() > 0 ? sample.first() : sample.second().rimTop() > 0 ? sample.second() : null;
            double fromBorder = (sample.weight() - 0.5) * 2;
            int top = closed == null ? 0 : closed.rimTop();
            if (closed != null && fromBorder < WALL && context.blockY() < top) {
                value = Math.max(value, 1 - fromBorder / WALL);
            }
        }
        return value;
    }

    /** Coarse grid of a stretched realm, in its own coordinates (its native noise cell: 8 blocks). */
    private static final int GRID = 8;
    private static final ThreadLocal<Object[]> CORNERS = ThreadLocal.withInitial(() -> new Object[] {-1, new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap()});

    /**
     * A stretched realm's function at a point: read in the realm's own coordinates (x / stretch) on its native 8-block
     * grid and interpolated, so stretching costs less than the realm's own generation.
     */
    private double stretched(DensityFunction child, FunctionContext context, int stretch, int nativeY) {
        double tx = (double) context.blockX() / stretch / GRID;
        double tz = (double) context.blockZ() / stretch / GRID;
        double ty = (double) nativeY / GRID;
        int gx = Mth.floor(tx);
        int gy = Mth.floor(ty);
        int gz = Mth.floor(tz);
        return Mth.lerp3(tx - gx, ty - gy, tz - gz,
            corner(child, gx, gy, gz), corner(child, gx + 1, gy, gz), corner(child, gx, gy + 1, gz), corner(child, gx + 1, gy + 1, gz),
            corner(child, gx, gy, gz + 1), corner(child, gx + 1, gy, gz + 1), corner(child, gx, gy + 1, gz + 1), corner(child, gx + 1, gy + 1, gz + 1));
    }

    private double corner(DensityFunction child, int gx, int gy, int gz) {
        Object[] cache = CORNERS.get();
        var values = (it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap) cache[1];
        if ((int) cache[0] != RealmLayout.generation || values.size() > 1 << 16) {
            values.clear();
            cache[0] = RealmLayout.generation;
        }
        long key = ((long) kind << 58) ^ ((long) (gx & 0x3FFFFFF) << 32) ^ ((long) (gy & 0x3F) << 26) ^ (gz & 0x3FFFFFF);
        double value = values.getOrDefault(key, Double.NaN);
        if (Double.isNaN(value)) {
            value = child.compute(new DensityFunction.SinglePointContext(gx * GRID, gy * GRID, gz * GRID));
            values.put(key, value);
        }
        return value;
    }

    /** The ice ridge behind the world border: its foot (just out of the sea) and its ragged peaks' heights. */
    private static final double RIDGE_FOOT = 140;
    private static final double RIDGE_LOW = 180;
    private static final double RIDGE_HIGH = 310;
    private static final double CRAG = 26;

    /** Wall thickness as a share of the blend zone. */
    private static final double WALL = 0.4;

    private double value(Realm realm, FunctionContext context) {
        int i = realm.ordinal();
        DensityFunction child = children[i];
        if (realm == Realm.OVERWORLD || !terrain) {
            return child != null ? child.compute(context) : 0;
        }
        // The realm's own (native) height: its terrain is lifted by its shift. Its y gradients are lifted when it is
        // wired (RealmWorld.gate); a stretched realm is read at native heights directly.
        int y = context.blockY() - realm.shift;
        if (child != null && y >= minY[i] && y < maxY[i]) {
            double value = realm.stretch > 1 ? stretched(child, context, realm.stretch, y) : child.compute(context);
            // An opened cave world: its roof and the upper part of its pillars fade away into the sky.
            return realm.openFrom != 0 && y > realm.openFrom ? value - (y - realm.openFrom) / 8.0 : value;
        }
        if (y < minY[i]) {
            return realm.solidFloor ? 1 : -1;
        }
        return -1;
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    /** Set while a NoiseChunk wires its router: that chunk's min block x, z (see NoiseChunkMixin). */
    public static final ThreadLocal<int[]> CHUNK = new ThreadLocal<>();

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        // A chunk wires only the realms around it: wiring every realm's noise graph for every chunk was a quarter of
        // all generation time (the visitors cache and hash whole graphs).
        int[] chunk = CHUNK.get();
        java.util.Set<Realm> around = chunk == null ? null : RealmLayout.realmsAround(chunk[0], chunk[1]);
        DensityFunction[] mapped = new DensityFunction[children.length];
        for (int i = 0; i < children.length; i++) {
            mapped[i] = children[i] == null || around != null && !around.contains(REALMS[i]) ? null : children[i].mapAll(visitor);
        }
        return visitor.apply(new RealmTerrain(mapped, minY, maxY, terrain, kind));
    }

    @Override
    public double minValue() {
        double min = -1;
        for (DensityFunction child : children) {
            if (child != null) {
                min = Math.min(min, child.minValue());
            }
        }
        return min;
    }

    @Override
    public double maxValue() {
        double max = 1;
        for (DensityFunction child : children) {
            if (child != null) {
                max = Math.max(max, child.maxValue());
            }
        }
        return max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }

    /**
     * Wraps the inside of a realm's interpolated functions: outside that realm (and its border) it returns 0 without
     * computing anything, so a chunk only pays for the noise of the realms it actually touches.
     */
    public record Gate(Realm realm, DensityFunction inner) implements DensityFunction {
        public static final MapCodec<Gate> CODEC = DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument")
            .xmap(inner -> new Gate(Realm.OVERWORLD, inner), Gate::inner);
        private static final KeyDispatchDataCodec<Gate> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);

        @Override
        public double compute(FunctionContext context) {
            RealmLayout.Sample sample = RealmLayout.sample(context.blockX(), context.blockZ());
            return sample.first() == realm || sample.near() == realm ? inner.compute(context) : 0;
        }

        @Override
        public void fillArray(double[] array, ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Gate(realm, inner.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Math.min(0, inner.minValue());
        }

        @Override
        public double maxValue() {
            return Math.max(0, inner.maxValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return KEY_CODEC;
        }
    }
}
