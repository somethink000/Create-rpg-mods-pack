package net.expedition.route;

import java.util.HashSet;
import java.util.Set;
import net.expedition.Expedition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Bosses live in their dungeons only: a boss (any story step's boss, any boss with a heart) that would appear in the
 * wild by itself (biome spawns, patrols) does not. Structures, lairs, spawners, eggs and commands
 * still spawn them.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class StoryBosses {
    private StoryBosses() {
    }

    private static volatile Set<EntityType<?>> bosses = Set.of();

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        Set<EntityType<?>> types = new HashSet<>();
        RouteSteps.all().forEach(entry -> entry.step().boss().ifPresent(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(types::add)));
        event.getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "loot_modifiers/boss_hearts.json"))
            .ifPresent(resource -> {
                try (var reader = resource.openAsReader()) {
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("bosses").forEach(boss ->
                        BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(boss.getAsJsonObject().get("entity").getAsString())).ifPresent(types::add));
                } catch (java.io.IOException e) {
                    Expedition.LOG.error("Story bosses: can't read the boss list", e);
                }
            });
        bosses = types;
        Expedition.LOG.info("Story bosses: {} boss types appear in their dungeons only", types.size());
    }

    /** A story boss (from the route or the boss-heart list). */
    public static boolean isBoss(EntityType<?> type) {
        return bosses.contains(type);
    }

    @SubscribeEvent
    static void onSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        // Not CHUNK_GENERATION: some mods' structures place their boss with it.
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.PATROL)
            && bosses.contains(event.getEntity().getType())) {
            event.setSpawnCancelled(true);
        }
    }
}
