package net.expedition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Lighter defaults for a new world: no spawn chunks kept loaded (vanilla keeps a few hundred chunks around spawn
 * loaded and ticking with nobody there; the camp needs none of it). Only a world that hasn't run yet is changed: an
 * operator's own gamerule stays.
 * <p>
 * Distant Horizons on a dedicated server builds far terrain only from chunks that exist (the pregenerated world, see
 * testserver/pregen.sh), never generates new terrain just to be seen from afar. The pack's config keeps FEATURES for
 * single player, where nothing is pregenerated; this API override isn't written to the file.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class ServerDefaults {
    private ServerDefaults() {
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer() && net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) {
            distantHorizonsPreExistingOnly();
        }
        ServerLevel overworld = event.getServer().overworld();
        if (overworld.getGameTime() < 200) {
            overworld.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, event.getServer());
            Expedition.LOG.info("New world: spawn chunks are not kept loaded (gamerule spawnChunkRadius 0)");
        }
    }

    /** By reflection: Distant Horizons is an optional mod, not a build dependency. */
    private static void distantHorizonsPreExistingOnly() {
        try {
            Object configs = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
            Object worldGen = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("worldGenerator").invoke(configs);
            Object mode = Class.forName("com.seibel.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig").getMethod("chunkGeneratorMode").invoke(worldGen);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class) Class.forName("com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode"), "PRE_EXISTING_ONLY");
            Object set = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("setValue", Object.class).invoke(mode, value);
            Expedition.LOG.info("Distant Horizons: far terrain from existing chunks only (PRE_EXISTING_ONLY) - {}", set);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Expedition.LOG.warn("Distant Horizons: couldn't set PRE_EXISTING_ONLY", e);
        }
    }
}
