package net.expedition.mixin;

import java.util.Optional;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create RNS deposits search their spot (a full noise column per candidate for Nether ones) before vanilla checks the
 * biome, and with realms every chunk of the world is a candidate for every deposit set: a third of all world generation
 * was Nether deposits computing columns outside the Nether realm. Skip the search unless the chunk's column has one of
 * the deposit's biomes at some height.
 */
@Mixin(targets = "com.bmaster.createrns.content.deposit.worldgen.DepositStructure")
abstract class DepositStructureMixin {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void expedition$biomeFirst(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        var biomes = ((Structure) (Object) this).biomes();
        int x = QuartPos.fromBlock(context.chunkPos().getMiddleBlockX());
        int z = QuartPos.fromBlock(context.chunkPos().getMiddleBlockZ());
        int minY = context.heightAccessor().getMinBuildHeight();
        for (int y = minY; y < context.heightAccessor().getMaxBuildHeight(); y += 32) {
            if (biomes.contains(context.biomeSource().getNoiseBiome(x, QuartPos.fromBlock(y), z, context.randomState().sampler()))) {
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }
}
