package net.expedition.world.realm;

import java.util.Optional;
import net.expedition.Expedition;
import net.expedition.mixin.HolderReferenceInvoker;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Land is never a table: real hill country rolls in long swells (worn flat lands only a little: plains, swamps and
 * deserts stay flat, so the relief tells the biomes apart), rise away from their rivers and are cut by gullies.
 * Tectonic shapes the surface from one function, {@code tectonic:depth} (terrain, caves, aquifers, underground rivers
 * and the biome depth all read it), so the relief is added there once. Swells lift the land the further inland it lies
 * (continentalness, so coasts and beaches stay) and the further from a river (the ridges parameter is near zero along
 * rivers, so they keep their water and get valleys); gullies cut into the land no deeper than it stands above the sea.
 */
final class Relief {
    private Relief() {
    }

    private static final ResourceKey<DensityFunction> DEPTH = function("tectonic", "depth");
    private static final ResourceKey<DensityFunction> OFFSET = function("tectonic", "terrain_spline/offset/final");
    private static final ResourceKey<DensityFunction> CONTINENTS = function("minecraft", "overworld/noise_router/continents");
    private static final ResourceKey<DensityFunction> RIDGES = function("minecraft", "overworld/noise_router/ridges");
    private static final ResourceKey<DensityFunction> EROSION = function("minecraft", "overworld/noise_router/erosion");
    /**
     * Flat biomes are where the land is worn (high erosion: plains, swamps, deserts' lowlands); there the swells die down
     * to this share, from {@code FLAT_FROM} erosion fully, so flat lands stay flat and hills stand out against them.
     */
    private static final double FLAT_SHARE = 0.15;
    private static final double FLAT_FROM = 0.45;
    private static final double FLAT_WIDTH = 0.6;
    private static final ResourceKey<NormalNoise.NoiseParameters> SWELL_NOISE = noise("relief_swell");
    private static final ResourceKey<NormalNoise.NoiseParameters> GULLY_NOISE = noise("relief_gully");

    /** Depth units per block (Tectonic's depth gradient falls by 1 per 128 blocks). */
    private static final double BLOCK = 1 / 128.0;
    /** Tectonic's surface offset at sea level. */
    private static final double SEA_OFFSET = -0.50375;
    /** Swells grow from nothing at the coast (this continentalness) to full a little inland (plus INLAND). */
    private static final double COAST = -0.17;
    private static final double INLAND = 0.08;
    /** ...and from nothing on a river (ridges 0) to full this far from it, in ridges. */
    private static final double RIVER = 0.02;
    private static final double VALLEY = 0.1;
    /** Gullies grow from nothing at sea level to full this high above it, faster than they can dig down. */
    private static final double RAMP = 20 * BLOCK;
    /** A swell lifts land by up to 1.5 times this: lowlands where the swell noise is low, uplands where it is high. */
    private static final double RISE = 45 * BLOCK;
    private static final double GULLY = 9 * BLOCK;
    /** Gullies where the gully noise is this close to zero: thin winding lines. */
    private static final double GULLY_WIDTH = 0.13;

    /** The depth function this server's registry already carries the relief in (registries live per server). */
    private static DensityFunction applied;

    static void apply(RegistryAccess registries) {
        var functions = registries.registryOrThrow(Registries.DENSITY_FUNCTION);
        var noises = registries.registryOrThrow(Registries.NOISE);
        Optional<Holder.Reference<DensityFunction>> depth = functions.getHolder(DEPTH);
        Optional<Holder.Reference<DensityFunction>> offset = functions.getHolder(OFFSET);
        Optional<Holder.Reference<DensityFunction>> continents = functions.getHolder(CONTINENTS);
        Optional<Holder.Reference<DensityFunction>> ridges = functions.getHolder(RIDGES);
        Optional<Holder.Reference<DensityFunction>> erosion = functions.getHolder(EROSION);
        if (depth.isEmpty() || offset.isEmpty() || continents.isEmpty() || ridges.isEmpty() || erosion.isEmpty()) {
            Expedition.LOG.info("Relief: no Tectonic terrain, left as it is");
            return;
        }
        if (depth.get().value() == applied) {
            return;
        }
        DensityFunction aboveSea = DensityFunctions.add(offset.get().value(), DensityFunctions.constant(-SEA_OFFSET));
        DensityFunction inland = ramp(continents.get().value(), COAST, INLAND);
        DensityFunction offRiver = ramp(ridges.get().value().abs(), RIVER, VALLEY);
        DensityFunction swell = DensityFunctions.noise(noises.getHolderOrThrow(SWELL_NOISE), 1, 0);
        // 1 where the land is rough, FLAT_SHARE where it is worn flat.
        DensityFunction rough = DensityFunctions.add(DensityFunctions.mul(erosion.get().value(), DensityFunctions.constant(-1 / FLAT_WIDTH)),
            DensityFunctions.constant(FLAT_FROM / FLAT_WIDTH + FLAT_SHARE)).clamp(FLAT_SHARE, 1);
        DensityFunction rise = DensityFunctions.mul(DensityFunctions.mul(DensityFunctions.mul(inland, offRiver), rough),
            DensityFunctions.mul(DensityFunctions.add(DensityFunctions.mul(swell, DensityFunctions.constant(1.5)), DensityFunctions.constant(0.5)).clamp(0, 1.5),
                DensityFunctions.constant(RISE)));
        DensityFunction across = DensityFunctions.noise(noises.getHolderOrThrow(GULLY_NOISE), 1, 0).abs();
        DensityFunction gully = DensityFunctions.mul(DensityFunctions.add(DensityFunctions.constant(1),
            DensityFunctions.mul(across, DensityFunctions.constant(-1 / GULLY_WIDTH))).clamp(0, 1).square(), DensityFunctions.constant(-GULLY));
        // Gullies measured from the risen ground: at most as deep as it stands above the sea.
        DensityFunction digMask = DensityFunctions.mul(DensityFunctions.add(aboveSea, rise), DensityFunctions.constant(1 / RAMP)).clamp(0, 1);
        DensityFunction relief = DensityFunctions.cache2d(DensityFunctions.add(rise, DensityFunctions.mul(digMask, gully)));
        DensityFunction rolled = DensityFunctions.cacheOnce(DensityFunctions.add(depth.get().value(), relief));
        @SuppressWarnings("unchecked")
        HolderReferenceInvoker<DensityFunction> holder = (HolderReferenceInvoker<DensityFunction>) (Object) depth.get();
        holder.expedition$bindValue(rolled);
        applied = rolled;
        Expedition.LOG.info("Relief: swells up to {} blocks and gullies {} deep on all land", (int) (1.5 * RISE / BLOCK), (int) (GULLY / BLOCK));
    }

    /** 0 at {@code from}, rising to 1 at {@code from + width}. */
    private static DensityFunction ramp(DensityFunction input, double from, double width) {
        return DensityFunctions.mul(DensityFunctions.add(input, DensityFunctions.constant(-from)), DensityFunctions.constant(1 / width)).clamp(0, 1);
    }

    private static ResourceKey<DensityFunction> function(String namespace, String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static ResourceKey<NormalNoise.NoiseParameters> noise(String path) {
        return ResourceKey.create(Registries.NOISE, ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, path));
    }
}
