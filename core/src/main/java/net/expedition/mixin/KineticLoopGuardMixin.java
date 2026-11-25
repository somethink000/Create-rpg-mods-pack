package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Create spreads a new speed one block deeper per call. A loop through a ratio block (Create Propulsion's Redstone
 * Transmission on an aircraft) fed a speed back into itself that changed a hair every lap without ever tripping
 * Create's own overspeed pop, and the stack ran out: the whole server went down. Past a depth no real shaft run
 * reaches, the block is popped the way Create pops an overspeed one.
 */
@Mixin(targets = "com.simibubi.create.content.kinetics.RotationPropagator", remap = false)
abstract class KineticLoopGuardMixin {
    @Unique
    private static final int expedition$MAX_DEPTH = 512;
    @Unique
    private static int expedition$depth;

    @WrapMethod(method = "propagateNewSource")
    private static void expedition$guard(KineticBlockEntity be, Operation<Void> original) {
        if (expedition$depth >= expedition$MAX_DEPTH) {
            BlockEntity block = (BlockEntity) (Object) be; // Create's ponder classes are not on our compile path
            if (block.getLevel() != null) block.getLevel().destroyBlock(block.getBlockPos(), true);
            return;
        }
        expedition$depth++;
        try {
            original.call(be);
        } finally {
            expedition$depth--;
        }
    }
}
