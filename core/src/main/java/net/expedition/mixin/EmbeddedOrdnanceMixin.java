package net.expedition.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create: The Air Wars looks up every entity's persistent data on every tick (creating it if missing) to find shells
 * stuck in the ground. Only projectiles get stuck: mobs, items, orbs, markers, frames and displays are skipped before
 * that lookup (a few percent of a busy tick).
 */
@Mixin(targets = "hi.util.EmbeddedOrdnance", remap = false)
abstract class EmbeddedOrdnanceMixin {
    private static boolean neverStuck(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Marker
            || entity instanceof Display || entity instanceof HangingEntity;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void expedition$onlyProjectiles(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (neverStuck(event.getEntity())) {
            ci.cancel();
        }
    }

    @Inject(method = "afterTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void expedition$onlyProjectilesAfter(EntityTickEvent.Post event, CallbackInfo ci) {
        if (neverStuck(event.getEntity())) {
            ci.cancel();
        }
    }
}
