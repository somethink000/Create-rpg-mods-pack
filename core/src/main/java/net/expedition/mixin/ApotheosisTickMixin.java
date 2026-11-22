package net.expedition.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apotheosis reads every entity's persistent data on every tick (gem arrows, bosses that burn in the sun): only arrows
 * and living things can have either, so items, orbs, markers and displays are skipped before the lookup.
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.AdventureEvents", remap = false)
abstract class ApotheosisTickMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void expedition$onlyArrowsAndLiving(EntityTickEvent.Post event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof LivingEntity) && !(event.getEntity() instanceof AbstractArrow)) {
            ci.cancel();
        }
    }
}
