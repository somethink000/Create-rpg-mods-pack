package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.expedition.garrison.DungeonBlocks;
import net.expedition.garrison.Garrisons;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/** While a hostile structure builds itself into a chunk (world generation or RoutePlacer), its blocks are recorded (DungeonBlocks). */
@Mixin(StructureStart.class)
abstract class StructureStartMixin {
    @WrapMethod(method = "placeInChunk")
    private void expedition$record(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox box,
        ChunkPos chunkPos, Operation<Void> original) {
        var id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(((StructureStart) (Object) this).getStructure());
        if (id == null || Garrisons.isPeacefulId(id.toString())) {
            original.call(level, manager, generator, random, box, chunkPos);
            return;
        }
        DungeonBlocks.beginBuild(level.getChunk(chunkPos.x, chunkPos.z));
        try {
            original.call(level, manager, generator, random, box, chunkPos);
        } finally {
            DungeonBlocks.endBuild();
        }
    }
}
