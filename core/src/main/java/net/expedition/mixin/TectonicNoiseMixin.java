package net.expedition.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tectonic's terrain noises (continentalness, erosion, ridges) are domain-warped by the vanilla shift noises. Without
 * its "smoother scaling" the warp is added after the coordinate is scaled down, so at large terrain scales a shift of a
 * few units jumps the noise by a hundred blocks every few blocks: heights come out choppy, knobs and steps on every
 * slope. With it the warp is a few blocks, as in vanilla, and slopes stay smooth at any scale.
 */
@Mixin(targets = "dev.worldgen.tectonic.worldgen.densityfunction.ConfigNoise", remap = false)
abstract class TectonicNoiseMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static boolean expedition$smootherScaling(boolean smootherScaling) {
        return true;
    }
}
