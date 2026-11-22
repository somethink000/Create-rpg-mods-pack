package net.expedition.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NTGL (the gun library of Create Gunsmithing) runs its client tickers straight off the live maps, and a ticker that
 * registers another one crashes the game with ConcurrentModificationException (seen in a test client). Run them off a copy.
 */
@Mixin(targets = "com.nukateam.ntgl.client.handlers.ClientTickHandler")
abstract class NtglTickersMixin {
    @Redirect(method = "clientTick", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"))
    private static <K, V> void expedition$overCopy(Map<K, V> tickers, BiConsumer<? super K, ? super V> action) {
        new HashMap<>(tickers).forEach(action);
    }
}
