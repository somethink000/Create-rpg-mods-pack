package net.expedition.mixin;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The realm world's feature order is merged per realm (see RealmWorld.mergedFeatureOrder). */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorFeaturesAccessor {
    @Mutable
    @Accessor("featuresPerStep")
    void expedition$setFeaturesPerStep(Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep);

    @Accessor("generationSettingsGetter")
    Function<Holder<Biome>, BiomeGenerationSettings> expedition$generationSettings();
}
