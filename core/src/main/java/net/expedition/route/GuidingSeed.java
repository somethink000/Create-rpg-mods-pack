package net.expedition.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The fireseed: thrown into a lit campfire it takes the fire (the campfire goes out) and a trail of flame streams from it
 * towards the nearest open story place of the thrower (its dungeon or boss), with the direction and distance above the
 * hotbar. This is how the player finds the way to the bosses still standing; the fallen ones are on the map (RouteMarkers).
 */
@EventBusSubscriber(modid = net.expedition.Expedition.MOD_ID)
public final class GuidingSeed {
    private GuidingSeed() {
    }

    /** How long the trail burns, and how far it reaches from the fire. */
    private static final int TRAIL_TICKS = 200;
    private static final double TRAIL_LENGTH = 28;
    private static final String[] DIRECTIONS = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

    private record Trail(ServerLevel level, Vec3 fire, Vec3 direction, long until) {
    }

    private static final List<Trail> TRAILS = new ArrayList<>();

    /**
     * Fire items thrown by players, watched until they burn or are gone: no listener on every entity's tick (each one
     * costs every entity of the world a call), only these few items are looked at.
     */
    private static final List<ItemEntity> THROWN = new ArrayList<>();

    @SubscribeEvent
    static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        ItemEntity item = event.getEntity();
        if (!item.level().isClientSide() && item.getItem().is(StoryItems.GUIDING_SEED.get())) {
            THROWN.add(item);
        }
    }

    private static void watchThrown() {
        THROWN.removeIf(item -> !item.isAlive() || item.getItem().isEmpty());
        for (ItemEntity item : List.copyOf(THROWN)) {
            inFire(item);
        }
    }

    private static void inFire(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level)) {
            return;
        }
        if (!item.getItem().is(StoryItems.GUIDING_SEED.get())) {
            return;
        }
        BlockPos at = item.blockPosition();
        BlockPos fire = lit(level.getBlockState(at)) ? at : lit(level.getBlockState(at.below())) ? at.below() : null;
        if (fire == null) {
            return;
        }
        ServerPlayer player = thrower(level, item, fire);
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }
        Vec3 center = Vec3.atBottomCenterOf(fire).add(0, 0.6, 0);
        // The seed takes the campfire's fire: it goes out (light it again for the next seed).
        BlockState state = level.getBlockState(fire);
        level.setBlock(fire, state.setValue(CampfireBlock.LIT, false), 11);
        CampfireBlock.dowse(null, level, fire, state);
        level.playSound(null, fire, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.8f, 1);
        level.playSound(null, fire, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1, 0.7f);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 12, 0.2, 0.2, 0.2, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 0.5, center.z, 30, 0.15, 0.6, 0.15, 0.02);
        if (player == null) {
            return;
        }
        Map.Entry<String, RouteData.Target> nearest = nearestTarget(player, level, center);
        if (nearest == null) {
            player.displayClientMessage(Component.translatable("expedition.seed.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        RouteData.Target target = nearest.getValue();
        Vec3 toTarget = new Vec3(target.pos.getX() + 0.5 - center.x, 0, target.pos.getZ() + 0.5 - center.z);
        int distance = (int) toTarget.length();
        Vec3 direction = toTarget.normalize();
        TRAILS.add(new Trail(level, center, direction, level.getGameTime() + TRAIL_TICKS));
        // Minecraft yaw: 0 = south, 90 = west; eight sectors of 45 degrees.
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        String side = DIRECTIONS[Math.floorMod(Math.round(yaw / 45), 8)];
        Component where = titleOf(nearest.getKey());
        player.displayClientMessage(Component.translatable("expedition.seed.points", Component.translatable("expedition.seed.dir." + side),
            where.copy().withStyle(ChatFormatting.GOLD), Math.round(distance / 50.0) * 50).withStyle(ChatFormatting.YELLOW), true);
    }

    private static boolean lit(BlockState state) {
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** Who threw it, or else the nearest player by the fire. */
    private static ServerPlayer thrower(ServerLevel level, ItemEntity item, BlockPos fire) {
        Entity owner = item.getOwner();
        if (owner instanceof ServerPlayer player) {
            return player;
        }
        return level.getNearestPlayer(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5, 16, false) instanceof ServerPlayer player ? player : null;
    }

    /**
     * The story place the seed shows, in this world: the easiest open one, as the story goes: of the earliest act, its
     * guardians first, then its bosses, its key bosses last; within those the route's order, then the nearest.
     */
    private static Map.Entry<String, RouteData.Target> nearestTarget(ServerPlayer player, ServerLevel level, Vec3 from) {
        UUID journal = Route.journalOf(player);
        String here = level.dimension().location().toString();
        Map.Entry<String, RouteData.Target> best = null;
        long bestRank = Long.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, RouteData.Target> entry : RouteData.get(player.getServer()).progress(journal).active.entrySet()) {
            if (!entry.getValue().dimension.equals(here)) {
                continue;
            }
            RouteSteps.Entry step = RouteSteps.all().stream().filter(s -> Route.id(s).equals(entry.getKey())).findFirst().orElse(null);
            long rank = step == null ? Long.MAX_VALUE : difficulty(step);
            double distance = from.distanceToSqr(Vec3.atCenterOf(entry.getValue().pos));
            if (rank < bestRank || rank == bestRank && distance < bestDistance) {
                best = entry;
                bestRank = rank;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A step's place in the story's climb: act, then guardian (0), boss (1) or key boss (2), then the route's order. */
    static long difficulty(RouteSteps.Entry entry) {
        int act = Acts.actOf(entry.step());
        Acts.Act chapter = Acts.get(act);
        int kind = entry.step().guardian().isPresent() ? 0 : chapter != null && chapter.keystones().containsKey(Route.id(entry)) ? 2 : 1;
        return act * 1_000_000L + kind * 10_000L + entry.step().order();
    }

    /** The step's title (its boss or dungeon name). */
    private static Component titleOf(String step) {
        return RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(step)).findFirst().map(Route::title)
            .orElse(Component.translatable("expedition.seed.guardian"));
    }

    /** The trail: a stream of flame rising from the fire and leaning away towards the place, a head running along it. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!THROWN.isEmpty() && event.getServer().getTickCount() % 5 == 0) {
            watchThrown();
        }
        if (TRAILS.isEmpty() || event.getServer().getTickCount() % 2 != 0) {
            return;
        }
        TRAILS.removeIf(trail -> trail.level.getGameTime() > trail.until);
        for (Trail trail : TRAILS) {
            long age = trail.until - trail.level.getGameTime();
            double head = 1 - (age % 40) / 40.0;
            for (int i = 0; i < 6; i++) {
                double t = Math.max(0, head - i * 0.04);
                Vec3 at = trail.fire.add(trail.direction.scale(TRAIL_LENGTH * t)).add(0, 1.5 + 5 * Math.sin(Math.PI * Math.min(1, t * 1.6)) * (1 - t * 0.5), 0);
                trail.level.sendParticles(i == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
            }
            trail.level.sendParticles(ParticleTypes.SMOKE, trail.fire.x, trail.fire.y + 0.3, trail.fire.z, 2, 0.15, 0.1, 0.15, 0.01);
        }
    }
}
