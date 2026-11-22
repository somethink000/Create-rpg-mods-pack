package net.expedition.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Nuclear works out every living entity's radiation on every tick (its data, its biome, its gear): ~4.5% of a
 * tick with a farm's animals around. Players keep it every tick; other creatures once a second (staggered), their dose
 * is only slower to build up.
 */
@Mixin(targets = "net.nuclearteam.createnuclear.content.radiation.capability.RadiationCapability", remap = false)
abstract class RadiationThrottleMixin {
    @Inject(method = "onEntityTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void expedition$playersEveryTick(EntityTickEvent.Pre event, CallbackInfo ci) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) && (entity.tickCount + entity.getId()) % 20 != 0) {
            ci.cancel();
        }
    }
}
