package net.expedition.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.expedition.Expedition;
import net.expedition.garrison.Garrisons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The lair of a guardian whose chapter the player hasn't reached is closed, as the Twilight Forest closes its lands:
 * near it (its frame and some way around) the player is pushed back out, in darkness and slowed, and hurt if they stay.
 * The story goes in order: a later chapter's dungeon that already stands (placed ahead for the prologue, or opened for
 * another player) can't be taken early. Creative and spectating players pass, and the author on the tour (Tour).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class LockedLairs {
    private LockedLairs() {
    }

    /** Closed this far around a lair's frame (at least MIN_RADIUS from its middle). */
    private static final int MARGIN = 48;
    private static final int MIN_RADIUS = 96;
    /** Seconds inside before it starts to hurt, and how much a second then. */
    private static final int GRACE = 5;
    private static final float HURT = 2;

    private static final Map<String, Integer> RADII = new HashMap<>();
    private static final Map<UUID, Integer> INSIDE = new HashMap<>();

    private record Closed(String step, RouteData.Target target, int act) {
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        RouteData data = RouteData.get(server);
        List<Closed> standing = new ArrayList<>();
        data.reserved.forEach((step, target) -> standing.add(new Closed(step, target, actOf(step))));
        data.all().values().forEach(progress -> progress.active.forEach((step, target) -> standing.add(new Closed(step, target, actOf(step)))));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator() || Tour.touring(player)) {
                INSIDE.remove(player.getUUID());
                continue;
            }
            RouteData.Progress progress = data.progress(Route.journalOf(player));
            String here = player.level().dimension().location().toString();
            Closed closed = standing.stream().filter(c -> c.act > progress.act && !progress.done.contains(c.step) && c.target.dimension.equals(here)
                && horizontal(player.position(), c.target) < radius(player, c)).findFirst().orElse(null);
            if (closed == null) {
                INSIDE.remove(player.getUUID());
                continue;
            }
            int seconds = INSIDE.merge(player.getUUID(), 1, Integer::sum);
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            Vec3 away = new Vec3(player.getX() - closed.target.pos.getX(), 0, player.getZ() - closed.target.pos.getZ()).normalize();
            player.push(away.x * 0.8, 0.2, away.z * 0.8);
            player.hurtMarked = true;
            if (seconds > GRACE) {
                player.hurt(player.damageSources().magic(), HURT);
            }
            player.displayClientMessage(Component.translatable("expedition.story.locked", closed.act).withStyle(ChatFormatting.DARK_GREEN), true);
        }
    }

    private static int actOf(String step) {
        return RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(step)).findFirst()
            .map(entry -> Acts.actOf(entry.step())).orElse(0);
    }

    private static double horizontal(Vec3 at, RouteData.Target target) {
        double dx = at.x - target.pos.getX() - 0.5;
        double dz = at.z - target.pos.getZ() - 0.5;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far the closed land reaches from the lair's middle: its frame's half-diagonal and the margin. */
    private static int radius(ServerPlayer player, Closed closed) {
        if (horizontal(player.position(), closed.target) > 600) {
            return 0; // far away: no need to look the frame up
        }
        return RADII.computeIfAbsent(closed.target.dungeonKey, key -> {
            Garrisons.Dungeon dungeon = closed.target.dungeonKey.startsWith(BossLair.KEY) ? null : Prologue.lairDungeon(player.serverLevel(), closed.target);
            if (dungeon == null) {
                return MIN_RADIUS;
            }
            BoundingBox box = dungeon.box();
            int half = (int) Math.sqrt(box.getXSpan() * box.getXSpan() + box.getZSpan() * box.getZSpan()) / 2;
            return Math.max(MIN_RADIUS, half + MARGIN);
        });
    }
}
