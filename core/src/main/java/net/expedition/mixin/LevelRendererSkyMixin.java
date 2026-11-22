package net.expedition.mixin;

import net.expedition.client.RealmAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sun, moon and stars fade with the rain in renderSky ("1 - rain"): a realm without a sun (the Nether, the Otherside,
 * the End, the Twilight Forest) hides them the same way, by as much as its air holds (RealmAtmosphere.sun).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererSkyMixin {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float expedition$noSunInRealms(ClientLevel level, float partialTick) {
        return 1 - (1 - level.getRainLevel(partialTick)) * RealmAtmosphere.sun();
    }
}
