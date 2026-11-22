package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FD Bosses' Geburah arena hangs over the void and checks for it as "first occupied height == -1", which is only true in
 * a dimension whose floor is y 0. In the End realm the void ends at the world's floor: compare against that.
 */
@Mixin(targets = "com.finderfeed.fdbosses.content.structures.GeburahArenaStructure")
abstract class GeburahArenaMixin {
    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0))
    private int expedition$voidAtWorldFloor(int y, @Local(argsOnly = true) Structure.GenerationContext context) {
        return y == context.heightAccessor().getMinBuildHeight() - 1 ? -1 : y;
    }
}
