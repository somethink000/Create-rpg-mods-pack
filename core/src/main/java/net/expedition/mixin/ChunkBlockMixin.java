package net.expedition.mixin;

import net.expedition.garrison.DungeonBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every block set in a chunk passes DungeonBlocks: built by a dungeon, or put over one of its blocks by world generation. */
@Mixin({ProtoChunk.class, LevelChunk.class})
abstract class ChunkBlockMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"))
    private void expedition$record(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        DungeonBlocks.onSet((ChunkAccess) (Object) this, pos, state);
    }
}
