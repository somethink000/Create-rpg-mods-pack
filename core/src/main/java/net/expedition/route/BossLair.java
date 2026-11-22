package net.expedition.route;

import net.expedition.Expedition;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A boss-only step of a dimension merged into the Overworld (the Ender Dragon: its fight belonged to the End
 * dimension) gets a lair: a spot on solid ground in that realm at the step's distance, marked like a dungeon. When a
 * player comes near, the boss appears there (the dragon with an exit podium, circling the lair).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class BossLair {
    private BossLair() {
    }

    static final String KEY = "lair|";
    private static final int SAMPLES = 400;
    private static final double WAKE_RADIUS = 160;
    private static final double BOSS_RADIUS = 320;

    /** True if this step is a merged realm's boss-only step. */
    public static boolean applies(RouteStep step) {
        return step.structures().isEmpty() && step.boss().isPresent() && realm(step) != null;
    }

    private static Realm realm(RouteStep step) {
        return RealmWorld.realmOf(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, step.dimension()));
    }

    /** Picks the lair: a point of the step's realm in its distance ring with ground under it. Null if none was found. */
    public static BlockPos find(ServerLevel level, RouteStep step, BlockPos center) {
        Realm realm = realm(step);
        var generator = level.getChunkSource().getGenerator();
        var random = level.getRandom();
        int max = step.maxDistance();
        for (int sample = 0; sample < SAMPLES; sample++) {
            if (sample > 0 && sample % 100 == 0) {
                max = max * 13 / 10; // the realm may lie a bit beyond the ring
            }
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = step.minDistance() + random.nextDouble() * Math.max(0, max - step.minDistance());
            int x = center.getX() + (int) (Math.cos(angle) * distance);
            int z = center.getZ() + (int) (Math.sin(angle) * distance);
            if (RealmWorld.realmAt(level, new BlockPos(x, 0, z)) != realm) {
                continue;
            }
            int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            if (y > level.getMinBuildHeight() + 8) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        for (RouteData.Progress progress : RouteData.get(server).all().values()) {
            for (RouteData.Target lair : progress.active.values()) {
                if (lair.summon != null) {
                    wake(server, level, lair);
                }
            }
        }
        RouteData.Target trial = Trial.current();
        if (trial != null && trial.summon != null) {
            wake(server, level, trial);
        }
    }

    private static void wake(MinecraftServer server, ServerLevel level, RouteData.Target lair) {
        BlockPos target = lair.pos;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(lair.summon));
        AABB around = new AABB(target).inflate(BOSS_RADIUS);
        var bosses = level.getEntities(type, around, Entity::isAlive);
        // The dragon doesn't save the point it circles: give it back after every reload.
        bosses.forEach(boss -> {
            if (boss instanceof EnderDragon dragon) {
                dragon.setFightOrigin(target);
                Guardians.bar(dragon); // its own bar belongs to the End's dragon fight, which the realm has not
            }
        });
        if (!bosses.isEmpty() || level.getNearestPlayer(target.getX(), target.getY(), target.getZ(), WAKE_RADIUS, false) == null) {
            return;
        }
        if (type == EntityType.ENDER_DRAGON && lair.dungeonKey.startsWith(KEY)) {
            new EndPodiumFeature(false).place(FeatureConfiguration.NONE, level, level.getChunkSource().getGenerator(), level.getRandom(), target);
        }
        // Above the ground of the place (a structure's origin can be inside its walls).
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
        Entity boss = type.spawn(level, ground.above(type == EntityType.ENDER_DRAGON ? 40 : 3), MobSpawnType.EVENT);
        if (boss instanceof EnderDragon dragon) {
            dragon.setFightOrigin(target);
            Guardians.bar(dragon);
        }
        Expedition.LOG.info("Route: {} rose at {}", lair.summon, target.toShortString());
    }
}
