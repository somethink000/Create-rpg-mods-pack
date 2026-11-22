package net.expedition.mixin;

import net.expedition.route.Prologue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The prologue shows the guardians' lairs from afar (the player is carried there as a spectator): the map (Antique
 * Atlas, through Surveyor) must not learn that land or those dungeons from it, the way is found by fireseeds.
 */
@Mixin(targets = "folk.sisby.surveyor.PlayerSummary$ServerPlayerEntitySummary$ServerPlayerExploration", remap = false)
abstract class SurveyorPrologueMixin {
    @Shadow
    public abstract ServerPlayer player();

    @Inject(method = "addChunk", at = @At("HEAD"), cancellable = true)
    private void expedition$notInPrologue(ResourceKey<Level> level, ChunkPos pos, boolean fromServer, CallbackInfo ci) {
        if (Prologue.running(player())) {
            ci.cancel();
        }
    }

    @Inject(method = "addStructure", at = @At("HEAD"), cancellable = true)
    private void expedition$noLairsInPrologue(ResourceKey<Level> level, ResourceKey<Structure> structure, ChunkPos pos, CallbackInfo ci) {
        if (Prologue.running(player())) {
            ci.cancel();
        }
    }
}
