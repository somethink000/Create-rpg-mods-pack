package net.expedition.world.realm;

import java.util.Optional;
import net.expedition.Expedition;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/**
 * The land of the round world (RealmLayout): continents, one per cell, with sea between them. Tectonic shapes all terrain and
 * the biomes' climate from one continentalness function ({@code tectonic:noise/full_continents}); here it is the
 * continent's own shape - deep sea along the straits and the rim, rising over a coast into land inland - with a share of
 * Tectonic's own noise on top for ragged coasts, bays and the odd lake. A strait is always sea (the noise never lifts it
 * above water), so every continent is a coast to sail to.
 */
public final class Continents implements DensityFunction {
    private static final ResourceKey<DensityFunction> FULL = ResourceKey.create(Registries.DENSITY_FUNCTION,
        ResourceLocation.fromNamespaceAndPath("tectonic", "noise/full_continents"));
    /** Continentalness of the straits' floor and of the open sea, and inland. */
    private static final double SEA = -0.36;
    private static final double LAND = 0.22;
    /** Blocks from the strait's shore over which the sea floor rises into land. */
    private static final double SHELF = 260;
    /** Tectonic's own noise on top (its values reach about +-1). */
    private static final double DETAIL = 0.12;
    /** Beyond the rim's sea: deeper still, down to this (not past -1.05: the climate's mushroom isles and islets lie there). */
    private static final double ABYSS = -0.85;

    private final DensityFunction noise;

    private Continents(DensityFunction noise) {
        this.noise = noise;
    }

    private static DensityFunction applied;

    /** Binds Tectonic's continentalness to the disc's continents (once per server's registries). */
    static void apply(RegistryAccess registries) {
        var functions = registries.registryOrThrow(Registries.DENSITY_FUNCTION);
        Optional<Holder.Reference<DensityFunction>> full = functions.getHolder(FULL);
        if (full.isEmpty()) {
            Expedition.LOG.info("Continents: no Tectonic terrain, the disc has no continents of its own");
            return;
        }
        if (full.get().value() == applied) {
            return;
        }
        DensityFunction shaped = DensityFunctions.flatCache(DensityFunctions.cache2d(new Continents(full.get().value())));
        @SuppressWarnings("unchecked")
        net.expedition.mixin.HolderReferenceInvoker<DensityFunction> holder = (net.expedition.mixin.HolderReferenceInvoker<DensityFunction>) (Object) full.get();
        holder.expedition$bindValue(shaped);
        applied = shaped;
        Expedition.LOG.info("Continents: a round world of {} blocks on a square sea, continents of ~{} with straits of {}", RealmLayout.RADIUS, RealmLayout.CELL, 2 * RealmLayout.STRAIT);
    }

    /** The continent's shape alone at a point (no noise): SEA in the straits and the rim's sea, LAND inland. */
    public static double shape(RealmLayout.Sample sample) {
        double coast = Math.min(sample.edge() - RealmLayout.STRAIT, sample.rim());
        double value = coast <= 0 ? SEA : SEA + (LAND - SEA) * Mth.smoothstep(Math.min(1, coast / SHELF));
        // Beyond the round world's edge the open sea deepens.
        return sample.rim() < 0 ? Math.max(ABYSS, SEA + sample.rim() / 400.0) : value;
    }

    @Override
    public double compute(FunctionContext context) {
        RealmLayout.Sample sample = RealmLayout.sample(context.blockX(), context.blockZ());
        double value = shape(sample);
        // Ragged coasts and inland lakes from Tectonic's own noise; never enough to close a strait.
        return value + DETAIL * Mth.clamp(noise.compute(context), -1, 1);
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new Continents(noise.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return ABYSS - DETAIL;
    }

    @Override
    public double maxValue() {
        return LAND + DETAIL;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(com.mojang.serialization.MapCodec.unit(this));
    }
}
