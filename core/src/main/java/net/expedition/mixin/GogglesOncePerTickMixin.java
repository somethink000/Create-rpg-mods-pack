package net.expedition.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create's "is the player wearing goggles" asks every registered check, and Gadgets and Gizmos' check walks all the Curios
 * slots. Create: The Air Wars' launcher overlay asks it every frame: ~6% of the render thread at 150 fps. For the client's
 * own player the answer is kept for the game tick (goggles go on and off by the tick anyway).
 */
@Mixin(targets = "com.simibubi.create.content.equipment.goggles.GogglesItem", remap = false)
abstract class GogglesOncePerTickMixin {
    @Unique
    private static long expedition$tick = Long.MIN_VALUE;
    @Unique
    private static boolean expedition$wearing;

    @Inject(method = "isWearingGoggles", at = @At("HEAD"), cancellable = true, require = 0)
    private static void expedition$cached(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && player == Minecraft.getInstance().player && player.level().getGameTime() == expedition$tick) {
            cir.setReturnValue(expedition$wearing);
        }
    }

    @Inject(method = "isWearingGoggles", at = @At("RETURN"), require = 0)
    private static void expedition$remember(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && player == Minecraft.getInstance().player) {
            expedition$tick = player.level().getGameTime();
            expedition$wearing = cir.getReturnValueZ();
        }
    }
}
