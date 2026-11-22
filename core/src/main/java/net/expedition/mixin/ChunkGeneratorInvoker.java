package net.expedition.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorInvoker {
    @Invoker("tryGenerateStructure")
    boolean expedition$tryGenerateStructure(StructureSet.StructureSelectionEntry entry, StructureManager structures, RegistryAccess registries,
                                             RandomState random, StructureTemplateManager templates, long seed, ChunkAccess chunk, ChunkPos pos, SectionPos section);
}
