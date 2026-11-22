package net.expedition.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.expedition.garrison.GarrisonData;
import net.expedition.garrison.GarrisonRecord;
import net.expedition.garrison.Garrisons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A walk through the route's dungeons in story order, for the author (/expedition tour): each step's dungeon - the one
 * the spawn branch gets (placed now if it doesn't stand yet, and kept for that branch, as the prologue does) - and a
 * teleport into its main hall, with the step's chapter, title, guardian and garrison in chat.
 */
public final class Tour {
    private Tour() {
    }

    private static final Map<UUID, Integer> AT = new HashMap<>();

    /**
     * Touring this session (the author walking the route): later chapters' lairs aren't closed to them (LockedLairs),
     * or every dungeon past the author's own chapter would be seen weakened, slowed and in the dark.
     */
    public static boolean touring(ServerPlayer player) {
        return AT.containsKey(player.getUUID()) || Trial.isStandIn(player);
    }

    /** Route steps that have a dungeon (a structure, or a boss lair), in story order: by chapter, then by their order. */
    static List<RouteSteps.Entry> stops() {
        return RouteSteps.all().stream().filter(entry -> !entry.step().structures().isEmpty() || BossLair.applies(entry.step()))
            .sorted(java.util.Comparator.comparingInt((RouteSteps.Entry entry) -> Acts.actOf(entry.step())).thenComparingInt(entry -> entry.step().order()))
            .toList();
    }

    public static void list(ServerPlayer player) {
        List<RouteSteps.Entry> stops = stops();
        player.sendSystemMessage(Component.literal("Маршрут: " + stops.size() + " подземелий (нажмите, чтобы перейти)").withStyle(ChatFormatting.GOLD));
        int act = -1;
        for (int i = 0; i < stops.size(); i++) {
            RouteSteps.Entry entry = stops.get(i);
            int stepAct = Acts.actOf(entry.step());
            if (stepAct != act) {
                act = stepAct;
                player.sendSystemMessage(Component.literal("Глава " + act).withStyle(ChatFormatting.YELLOW));
            }
            player.sendSystemMessage(button("  " + (i + 1) + ". ", "/expedition tour " + (i + 1), "Перейти").append(Route.title(entry)));
        }
    }

    public static void go(ServerPlayer player, int number) {
        List<RouteSteps.Entry> stops = stops();
        if (number < 1 || number > stops.size()) {
            player.sendSystemMessage(Component.literal("Номер от 1 до " + stops.size()).withStyle(ChatFormatting.RED));
            return;
        }
        AT.put(player.getUUID(), number);
        RouteSteps.Entry entry = stops.get(number - 1);
        MinecraftServer server = player.getServer();
        player.sendSystemMessage(Component.literal("Ищу место подземелья " + number + "…").withStyle(ChatFormatting.GRAY));
        if (BossLair.applies(entry.step())) {
            BlockPos lair = BossLair.find(server.overworld(), entry.step(), Branches.middle(server, 0));
            RouteData.Target target = null;
            if (lair != null) {
                target = new RouteData.Target();
                target.dimension = server.overworld().dimension().location().toString();
                target.pos = lair;
                target.dungeonKey = BossLair.KEY + entry.step().boss().get() + "|" + lair.getX() + "," + lair.getZ();
            }
            arrive(player, number, stops.size(), entry, target);
            return;
        }
        Route.reserve(server, Route.id(entry), target -> arrive(player, number, stops.size(), entry, target));
    }

    public static void step(ServerPlayer player, int delta) {
        go(player, Math.max(1, Math.min(stops().size(), AT.getOrDefault(player.getUUID(), 0) + delta)));
    }

    private static void arrive(ServerPlayer player, int number, int count, RouteSteps.Entry entry, RouteData.Target target) {
        if (player.isRemoved()) {
            return;
        }
        if (target == null) {
            player.sendSystemMessage(Component.literal("Подземелье " + Route.id(entry) + " не удалось разместить (см. лог сервера)").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(target.dimension)));
        if (level == null) {
            level = player.getServer().overworld();
        }
        // Into the main hall (the prologue's camera spot), else above the dungeon.
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        Shots.Path hall = dungeon == null ? null : Shots.inside(level, dungeon.start());
        Vec3 to = hall != null ? hall.from() : Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos).above(2));
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());

        RouteStep step = entry.step();
        GarrisonRecord record = dungeon == null ? null : GarrisonData.get(level).find(dungeon.key());
        String garrison = record == null ? "не начат" : switch (record.state) {
            case PENDING -> "не начат";
            case ACTIVE -> "идёт: осталось " + record.remaining() + " из " + record.total;
            case CLEARED -> "зачищен";
        };
        String foe = step.boss().map(id -> "босс " + id).orElse(step.guardian().map(g -> "страж " + g.entity() + " (" + (int) g.health() + " HP)").orElse("без стража"));
        BlockPos spawn = player.getServer().overworld().getSharedSpawnPos();
        int distance = (int) Math.sqrt(spawn.distSqr(target.pos));
        player.sendSystemMessage(Component.literal("─── " + number + "/" + count + " · Глава " + Acts.actOf(step) + " · ").withStyle(ChatFormatting.GOLD)
            .append(Route.title(entry).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.literal(Route.id(entry) + " · " + (dungeon != null ? dungeon.id() : target.dungeonKey) + " · " + foe).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Гарнизон: " + garrison + " · " + distance + " бл. от спавна · " + target.pos.toShortString()
            + (hall == null ? " (зал не найден, вы над подземельем)" : "")).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(button("[« назад]", "/expedition tour prev", "Предыдущее подземелье").append(" ")
            .append(button("[дальше »]", "/expedition tour next", "Следующее подземелье")).append(" ")
            .append(button("[список]", "/expedition tour list", "Все подземелья маршрута")));
        // The step's approach as a player gets it (scene, guardian), though the author's journal hasn't opened it yet.
        if (dungeon != null) {
            Route.arrive(player, entry, target);
        }
    }

    private static MutableComponent button(String text, String command, String hover) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }
}
