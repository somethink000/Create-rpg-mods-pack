package net.expedition.mixin;

import net.expedition.client.RealmAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sky's colour in a realm is the realm's (RealmAtmosphere): a Nether red, an End violet, a Twilight dusk. */
@Mixin(ClientLevel.class)
abstract class ClientLevelSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void expedition$realmSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(RealmAtmosphere.sky(cir.getReturnValue()));
    }
}
