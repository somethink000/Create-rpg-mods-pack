package net.expedition.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaolib (needed by CBC Military Supplement) copies, snapshots and saves every chunk that loads, for its projectile
 * broad phase: measured 30% of the server's CPU while walking and a cache half the size of the world on disk. Shells
 * work without it (a chunk with no snapshot is read live if loaded, and Shaolib prefetches the snapshot on demand), so
 * the eager copy on chunk load is skipped.
 */
@Mixin(targets = "com.verr1.shaolib.cache.neoforge.NeoForgeChunkCacheService")
abstract class ShaolibChunkCacheMixin {
    @Inject(method = "onChunkLoad", at = @At("HEAD"), cancellable = true)
    private void expedition$noEagerSnapshot(ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        ci.cancel();
    }
}
