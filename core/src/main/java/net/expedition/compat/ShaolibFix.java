package net.expedition.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.expedition.Expedition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Shaolib 0.1.0 (needed by CBC Military Supplement) always registers its Synaxis block-body resolver and hooks every
 * explosion into it; without the Synaxis mod the first explosion crashes the server (NoClassDefFoundError). Before the
 * server starts, the Synaxis resolver is dropped from shaolib's resolver list; the Sable one (ships) stays.
 * Reflection only, so the pack core doesn't depend on shaolib.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class ShaolibFix {
    private ShaolibFix() {
    }

    private static final String SERVICES = "com.verr1.shaolib.api.block.BlockBodyResolverServices";
    private static final String COMPOSITE = "com.verr1.shaolib.api.block.CompositeBlockBodyResolver";
    private static final String SYNAXIS_RESOLVER = "com.verr1.shaolib.compat.synaxis.SynaxisBlockBodyResolver";

    @SubscribeEvent
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!ModList.get().isLoaded("shaolib") || ModList.get().isLoaded("synaxis")) {
            return;
        }
        try {
            Class<?> services = Class.forName(SERVICES);
            Object resolver = services.getMethod("get").invoke(null);
            int removed = 0;
            if (resolver != null && resolver.getClass().getName().equals(COMPOSITE)) {
                Field field = resolver.getClass().getDeclaredField("resolvers");
                field.setAccessible(true);
                List<?> resolvers = (List<?>) field.get(resolver);
                List<?> kept = resolvers.stream().filter(r -> !r.getClass().getName().equals(SYNAXIS_RESOLVER)).toList();
                removed = resolvers.size() - kept.size();
                if (removed > 0) {
                    Method of = resolver.getClass().getMethod("of", Class.forName("[Lcom.verr1.shaolib.api.block.BlockBodyResolver;"));
                    Object array = java.lang.reflect.Array.newInstance(Class.forName("com.verr1.shaolib.api.block.BlockBodyResolver"), kept.size());
                    for (int i = 0; i < kept.size(); i++) {
                        java.lang.reflect.Array.set(array, i, kept.get(i));
                    }
                    services.getMethod("set", Class.forName("com.verr1.shaolib.api.block.BlockBodyResolver")).invoke(null, of.invoke(null, array));
                }
            } else if (resolver != null && resolver.getClass().getName().equals(SYNAXIS_RESOLVER)) {
                services.getMethod("reset").invoke(null);
                removed = 1;
            }
            Expedition.LOG.info("Shaolib fix: removed {} Synaxis resolver(s) (Synaxis is not installed)", removed);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Expedition.LOG.error("Shaolib fix failed: explosions may crash the server", error);
        }
    }
}
