package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A lifted realm's own structures are laid out in its native heights and lifted with its terrain (see LiftedGenerator). */
@Mixin(Structure.class)
abstract class StructureLiftMixin {
    @ModifyVariable(method = "generate", at = @At("HEAD"), argsOnly = true)
    private ChunkGenerator expedition$nativeHeights(ChunkGenerator generator) {
        return RealmWorld.liftedFor((Structure) (Object) this, generator);
    }

    @ModifyVariable(method = "generate", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.world.level.LevelHeightAccessor expedition$nativeRange(net.minecraft.world.level.LevelHeightAccessor heights) {
        return RealmWorld.heightsFor((Structure) (Object) this, heights);
    }

    @ModifyReturnValue(method = "generate", at = @At("RETURN"))
    private StructureStart expedition$lift(StructureStart start) {
        int shift = RealmWorld.liftOf((Structure) (Object) this);
        if (shift != 0 && start.isValid()) {
            start.getPieces().forEach(piece -> piece.move(0, shift, 0));
        }
        return start;
    }
}
