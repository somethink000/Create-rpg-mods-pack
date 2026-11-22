package net.expedition.mixin;

import net.expedition.world.realm.RealmWorld;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the realm world each realm gets its own fluids; the Overworld part keeps its aquifers. */
@Mixin(NoiseChunk.class)
abstract class NoiseChunkMixin {
    @Shadow @Final @Mutable
    private Aquifer aquifer;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private void expedition$wireChunkRealms(int cellCountXZ, RandomState random, int firstNoiseX, int firstNoiseZ, NoiseSettings noiseSettings,
                                            DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
                                            Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo ci) {
        if (RealmWorld.isRealmSettings(settings)) {
            net.expedition.world.realm.RealmTerrain.CHUNK.set(new int[] {firstNoiseX, firstNoiseZ});
        }
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private void expedition$wiredChunkRealms(int cellCountXZ, RandomState random, int firstNoiseX, int firstNoiseZ, NoiseSettings noiseSettings,
                                             DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
                                             Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo ci) {
        net.expedition.world.realm.RealmTerrain.CHUNK.remove();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void expedition$realmFluids(int cellCountXZ, RandomState random, int firstNoiseX, int firstNoiseZ, NoiseSettings noiseSettings,
                                        DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
                                        Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo ci) {
        if (RealmWorld.isRealmSettings(settings)) {
            aquifer = new RealmWorld.RealmAquifer(aquifer);
        }
    }
}
