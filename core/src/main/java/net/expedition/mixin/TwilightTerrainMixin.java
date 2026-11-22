package net.expedition.mixin;

import java.util.Optional;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Twilight's terrain follows the biomes of the realm world's Twilight cells (see RealmWorld.twilightTerrainBiome). */
@Mixin(targets = "twilightforest.world.components.layer.BiomeDensitySource", remap = false)
abstract class TwilightTerrainMixin {
    @Shadow
    public abstract Optional<?> getTerrainColumn(ResourceKey<Biome> biome);

    @Inject(method = "getTerrainColumn(II)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void expedition$cellBiome(int gridX, int gridZ, CallbackInfoReturnable<Optional<?>> cir) {
        ResourceKey<Biome> biome = RealmWorld.twilightTerrainBiome(gridX, gridZ);
        if (biome != null) {
            // A biome without terrain data of its own (not a key biome of the grid) gets a flat clearing.
            Optional<?> column = getTerrainColumn(biome);
            cir.setReturnValue(column.isPresent() ? column : getTerrainColumn(RealmWorld.TWILIGHT_FLAT));
        }
    }
}
