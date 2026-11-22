package net.expedition.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;

/** Empty on purpose: applied after Sable (priority) so ExpeditionMixinPlugin.postApply can patch Sable's merged wrapper. */
@Mixin(value = Particle.class, priority = 2000)
abstract class ParticleSablePoolMixin {
}
