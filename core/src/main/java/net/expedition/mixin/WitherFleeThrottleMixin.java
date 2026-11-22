package net.expedition.mixin;

import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wither Reincarnated gives every pathfinding mob a goal to flee the Wither, which searches 64 blocks around for it on
 * every AI tick (~2% of a tick with a farm). Looked for every 10 ticks instead (staggered per goal): a mob starts to
 * flee half a second later.
 */
@Mixin(targets = "com.alexander.witherreincarnated.events.ModifyAIEvent$5", remap = false)
abstract class WitherFleeThrottleMixin {
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void expedition$everyTenTicks(CallbackInfoReturnable<Boolean> cir) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && (server.getTickCount() + System.identityHashCode(this)) % 10 != 0) {
            cir.setReturnValue(false);
        }
    }
}
