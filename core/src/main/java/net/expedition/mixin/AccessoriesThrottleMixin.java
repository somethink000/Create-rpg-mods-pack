package net.expedition.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accessories looks through every living entity's accessory slots on every tick (~3% of a tick with many mobs), though
 * only players wear accessories here. Players keep it every tick; other creatures every 10 ticks (staggered).
 */
@Mixin(targets = "io.wispforest.accessories.neoforge.AccessoriesForge", remap = false)
abstract class AccessoriesThrottleMixin {
    @Inject(method = "onLivingEntityTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void expedition$playersEveryTick(EntityTickEvent.Pre event, CallbackInfo ci) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) && (entity.tickCount + entity.getId()) % 10 != 0) {
            ci.cancel();
        }
    }
}
