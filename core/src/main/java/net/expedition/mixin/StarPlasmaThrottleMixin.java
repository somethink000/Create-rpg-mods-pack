package net.expedition.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With its custom sky off (the pack's setting: "return to standard Minecraft sky"), Cosmonautics still works out and draws
 * the universe and the planet beneath after the sky every frame (~5% of the render thread on the ground): skipped then.
 * Deep space never draws there anyway (its sky comes from the dimension's effects).
 * <p>Cosmonautics (RocketNautics) rebuilds its 64x64 star-plasma sky texture from warped fractal noise on the render thread
 * every game tick, on the ground too: a client profile showed 20% of the render thread in it. Once a second is enough
 * for a slowly drifting texture.
 */
@Mixin(targets = "dev.devce.rocketnautics.client.SkyHandler")
abstract class StarPlasmaThrottleMixin {
    @Shadow
    public static ResourceLocation STAR_PLASMA_TEXTURE_ID;
    @Shadow
    private static long lastPlasmaUpdateTick;

    @Unique
    private static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue expedition$customSky;
    @Unique
    private static boolean expedition$noConfig;

    @Inject(method = "onRenderLevelStage", at = @At("HEAD"), cancellable = true)
    private static void expedition$noUniverseOnStandardSky(net.neoforged.neoforge.client.event.RenderLevelStageEvent event, CallbackInfo ci) {
        if (expedition$noConfig || event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        try {
            if (expedition$customSky == null) {
                Object client = Class.forName("dev.devce.rocketnautics.RocketConfig").getField("CLIENT").get(null);
                expedition$customSky = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) client.getClass().getField("enableCustomSky").get(client);
            }
            if (!expedition$customSky.get()) {
                ci.cancel();
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            expedition$noConfig = true; // a renamed config field: the mod is left as it is
        }
    }

    @Inject(method = "ensureStarPlasmaTexture", at = @At("HEAD"), cancellable = true)
    private static void expedition$oncePerSecond(CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (STAR_PLASMA_TEXTURE_ID != null && level != null && Math.abs(level.getGameTime() - lastPlasmaUpdateTick) < 20) {
            ci.cancel();
        }
    }
}
