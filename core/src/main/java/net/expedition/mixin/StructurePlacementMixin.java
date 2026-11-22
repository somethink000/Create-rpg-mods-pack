package net.expedition.mixin;

import net.expedition.world.UnifiedStructures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filler dungeons are placed by {@link UnifiedStructures} only: the mods' own placement of them is cancelled. */
@Mixin(ChunkGenerator.class)
abstract class StructurePlacementMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void expedition$nativeOff(StructureSet.StructureSelectionEntry entry, StructureManager structures, RegistryAccess registries, RandomState random,
                                      StructureTemplateManager templates, long seed, ChunkAccess chunk, ChunkPos pos, SectionPos section,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!UnifiedStructures.allowNative(entry.structure().value())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"))
    private void expedition$beginChunk(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk,
                                       StructureTemplateManager templates, CallbackInfo ci) {
        UnifiedStructures.beginChunk((ChunkGenerator) (Object) this);
    }

    @Inject(method = "createStructures", at = @At("TAIL"))
    private void expedition$cellDungeon(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk,
                                        StructureTemplateManager templates, CallbackInfo ci) {
        UnifiedStructures.placeCellDungeon((ChunkGenerator) (Object) this, registries, state, structures, chunk, templates);
    }
}
