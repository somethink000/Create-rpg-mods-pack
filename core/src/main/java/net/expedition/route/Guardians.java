package net.expedition.route;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.expedition.Expedition;
import net.expedition.garrison.Garrisons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Guardians of the dungeons that have no boss of their own (RouteStep.Guardian): a creature made into a boss for its
 * chapter, risen in the dungeon's main hall when a player comes in, marked with its step (only its death completes the
 * step: a zombie elsewhere is a zombie), with a boss bar for the players around it.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class Guardians {
    private Guardians() {
    }

    /** Persistent-data key: the step a guardian stands for. */
    public static final String TAG = "expedition_guardian";
    /** A player inside this long before a missing guardian rises again (its entity loads a moment after its chunk). */
    private static final int GRACE_SECONDS = 5;
    private static final double BAR_RADIUS = 48;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    /** Per step: seconds a player has been inside with no guardian found. */
    private static final Map<String, Integer> MISSING = new HashMap<>();

    /** The step a living entity guards, or null. */
    public static String stepOf(Entity entity) {
        String step = entity.getPersistentData().getString(TAG);
        return step.isEmpty() ? null : step;
    }

    /**
     * The step's guardian in its dungeon, if risen and loaded; else a creature of its kind the dungeon placed itself
     * (a boss of its own: the monument's elder guardian, the ship's captain), which becomes the guardian.
     */
    static LivingEntity find(ServerLevel level, String step, RouteStep.Guardian spec, RouteData.Target target) {
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        AABB area = dungeon != null ? AABB.of(dungeon.box()).inflate(16) : new AABB(target.pos).inflate(96);
        var around = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive()
            && (step.equals(stepOf(entity)) || stepOf(entity) == null && StoryBosses.isBoss(entity.getType()) && EntityType.getKey(entity.getType()).equals(spec.entity())));
        LivingEntity guardian = around.stream().filter(entity -> step.equals(stepOf(entity))).findFirst().orElse(around.isEmpty() ? null : around.get(0));
        if (guardian != null && !EntityType.getKey(guardian.getType()).equals(spec.entity())) {
            // A guardian of an older route (the crypt's skeleton before its Nightmare): its successor takes its place.
            Expedition.LOG.info("Guardian of {}: {} replaced by {}", step, EntityType.getKey(guardian.getType()), spec.entity());
            guardian.discard();
            guardian = around.stream().filter(entity -> entity.isAlive() && stepOf(entity) == null).findFirst().orElse(null);
        }
        if (guardian != null && stepOf(guardian) == null) {
            make(guardian, step, spec);
            Expedition.LOG.info("Guardian of {}: the dungeon's own {} at {}", step, spec.entity(), guardian.blockPosition().toShortString());
        }
        return guardian;
    }

    /** The guardian of a step, risen now in the dungeon's main hall if it isn't there. Null if the step has none. */
    public static LivingEntity rise(ServerLevel level, String step, RouteStep route, RouteData.Target target) {
        RouteStep.Guardian spec = route.guardian().orElse(null);
        if (spec == null) {
            return null;
        }
        LivingEntity existing = find(level, step, spec, target);
        if (existing != null || !spec.rise()) {
            return existing;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(spec.entity()).orElse(null);
        if (type == null) {
            Expedition.LOG.warn("Guardian of {}: unknown entity {}", step, spec.entity());
            return null;
        }
        BlockPos at = spec.water() || Garrisons.swims(level, spec.entity().toString()) ? water(level, target) : hall(level, target);
        if (!(type.spawn(level, at, MobSpawnType.EVENT) instanceof LivingEntity guardian)) {
            return null;
        }
        make(guardian, step, spec);
        Expedition.LOG.info("Guardian of {} ({}) rose at {}", step, spec.entity(), at.toShortString());
        return guardian;
    }

    /** Makes a creature the step's guardian: marked, strengthened, named (unless it is a boss with a name of its own), barred. */
    private static void make(LivingEntity guardian, String step, RouteStep.Guardian spec) {
        guardian.getPersistentData().putString(TAG, step);
        set(guardian, Attributes.SCALE, spec.scale());
        if (spec.health() > 0) {
            set(guardian, Attributes.MAX_HEALTH, spec.health());
        }
        if (spec.damage() > 0) {
            set(guardian, Attributes.ATTACK_DAMAGE, spec.damage());
        }
        if (spec.armor() > 0) {
            set(guardian, Attributes.ARMOR, base(guardian, Attributes.ARMOR) + spec.armor());
        }
        set(guardian, Attributes.KNOCKBACK_RESISTANCE, Math.max(base(guardian, Attributes.KNOCKBACK_RESISTANCE), 0.7));
        guardian.setHealth(guardian.getMaxHealth());
        if (!StoryBosses.isBoss(guardian.getType())) {
            guardian.setCustomName(Component.translatable("expedition.guardian." + step).withStyle(ChatFormatting.GOLD));
        }
        if (guardian instanceof Mob mob) {
            mob.setPersistenceRequired();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0);
            }
        }
        track(guardian);
    }

    /**
     * Where a guardian rises: the main hall (the interior shot's point) if its floor is the dungeon's own; else the
     * dungeon's floor nearest its middle (inside one of its pieces, on its blocks, not water or air beside it - a ship's
     * deck, not the sea).
     */
    private static BlockPos hall(ServerLevel level, RouteData.Target target) {
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        if (dungeon == null) {
            return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos).above();
        }
        Shots.Path inside = Shots.inside(level, dungeon.start());
        if (inside != null) {
            BlockPos eye = BlockPos.containing(inside.from());
            for (BlockPos floor = eye; floor.getY() > eye.getY() - 5; floor = floor.below()) {
                if (Garrisons.isFloor(level, floor) && Garrisons.inPiece(level, dungeon, floor)) {
                    return floor;
                }
            }
        }
        var box = dungeon.box();
        BlockPos middle = box.getCenter();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var random = level.getRandom();
        for (int sample = 0; sample < 200; sample++) {
            int spread = 2 + sample / 4;
            int x = Math.max(box.minX(), Math.min(box.maxX(), middle.getX() + random.nextInt(2 * spread + 1) - spread));
            int z = Math.max(box.minZ(), Math.min(box.maxZ(), middle.getZ() + random.nextInt(2 * spread + 1) - spread));
            for (int y = box.minY() + 1; y < box.maxY(); y++) {
                BlockPos at = new BlockPos(x, y, z);
                double distance = at.distSqr(middle);
                if (distance < bestDistance && Garrisons.isFloor(level, at) && Garrisons.inPiece(level, dungeon, at)) {
                    bestDistance = distance;
                    best = at;
                }
            }
        }
        if (best != null) {
            return best;
        }
        return inside != null ? BlockPos.containing(inside.from()) : level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos).above();
    }

    /** Where a swimming guardian rises (the Leviathan, the Kraken): water three deep in the dungeon, nearest its middle. */
    private static BlockPos water(ServerLevel level, RouteData.Target target) {
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        var box = dungeon != null ? dungeon.box().inflatedBy(16) : new net.minecraft.world.level.levelgen.structure.BoundingBox(target.pos).inflatedBy(48);
        BlockPos middle = box.getCenter();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var random = level.getRandom();
        for (int sample = 0; sample < 400; sample++) {
            int x = box.minX() + random.nextInt(box.getXSpan());
            int z = box.minZ() + random.nextInt(box.getZSpan());
            int y = box.minY() + random.nextInt(box.getYSpan());
            BlockPos at = new BlockPos(x, y, z);
            boolean deep = level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER) && level.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER)
                && level.getFluidState(at.above(2)).is(net.minecraft.tags.FluidTags.WATER);
            if (deep && at.distSqr(middle) < bestDistance) {
                bestDistance = at.distSqr(middle);
                best = at;
            }
        }
        return best != null ? best : hall(level, target);
    }

    private static double base(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0 : instance.getBaseValue();
    }

    private static void set(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** A bar for a boss that has none of its own here (the dragon away from the End's fight), shown as a guardian's. */
    static void bar(LivingEntity boss) {
        BARS.computeIfAbsent(boss.getUUID(), id -> new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10));
    }

    private static void track(LivingEntity guardian) {
        String step = stepOf(guardian);
        boolean bar = RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(step)).findFirst()
            .flatMap(entry -> entry.step().guardian()).map(RouteStep.Guardian::bar).orElse(true);
        if (bar) {
            BARS.computeIfAbsent(guardian.getUUID(), id -> new ServerBossEvent(guardian.getDisplayName(), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10));
        }
    }

    /**
     * A guardian loaded again (after a restart, or its chunk) gets its bar back, and its step's health if that changed
     * since it rose (a rebalanced route reaches a world's standing bosses), its wounds kept in proportion.
     */
    /** The health bonus balance_bosses.js once gave every boss (saved with it): the route's health replaces it. */
    private static final net.minecraft.resources.ResourceLocation OLD_BONUS = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("kubejs", "boss_balance_health");

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity boss && StoryBosses.isBoss(boss.getType())) {
            AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
            if (health != null && health.getModifier(OLD_BONUS) != null) {
                float share = boss.getHealth() / Math.max(1, boss.getMaxHealth());
                health.removeModifier(OLD_BONUS);
                boss.setHealth(Math.max(1, boss.getMaxHealth() * share));
            }
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living && stepOf(living) != null) {
            String step = stepOf(living);
            RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(step)).findFirst().flatMap(entry -> entry.step().guardian())
                .filter(spec -> spec.health() > 0 && Math.abs(base(living, Attributes.MAX_HEALTH) - spec.health()) > 0.5).ifPresent(spec -> {
                    float share = living.getHealth() / Math.max(1, living.getMaxHealth());
                    set(living, Attributes.MAX_HEALTH, spec.health());
                    living.setHealth(Math.max(1, living.getMaxHealth() * share));
                });
            track(living);
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 5 == 0) {
            updateBars(server);
        }
        if (server.getTickCount() % 20 == 0) {
            riseWhereEntered(server);
        }
    }

    private static void updateBars(MinecraftServer server) {
        BARS.entrySet().removeIf(entry -> {
            Entity entity = null;
            for (ServerLevel level : server.getAllLevels()) {
                entity = level.getEntity(entry.getKey());
                if (entity != null) {
                    break;
                }
            }
            ServerBossEvent bar = entry.getValue();
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                bar.removeAllPlayers();
                return true;
            }
            bar.setProgress(living.getHealth() / living.getMaxHealth());
            Vec3 at = living.position();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean near = player.level() == living.level() && player.position().distanceToSqr(at) < BAR_RADIUS * BAR_RADIUS;
                if (near && !bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                } else if (!near && bar.getPlayers().contains(player)) {
                    bar.removePlayer(player);
                }
            }
            return false;
        });
    }

    /** Players in a dungeon whose guardian isn't there (not yet risen, or left behind unloaded): it rises. */
    private static void riseWhereEntered(MinecraftServer server) {
        RouteData data = RouteData.get(server);
        java.util.Set<String> inside = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            RouteData.Progress progress = data.progress(Route.journalOf(player));
            String here = player.level().dimension().location().toString();
            for (Map.Entry<String, RouteData.Target> active : progress.active.entrySet()) {
                RouteData.Target target = active.getValue();
                if (!target.dimension.equals(here) || !Route.arrived(player, target)) {
                    continue;
                }
                RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(active.getKey()) && entry.step().guardian().isPresent()).findFirst()
                    .ifPresent(entry -> {
                        inside.add(active.getKey());
                        if (find(player.serverLevel(), active.getKey(), entry.step().guardian().get(), target) != null) {
                            MISSING.remove(active.getKey());
                        } else if (MISSING.merge(active.getKey(), 1, Integer::sum) > GRACE_SECONDS) {
                            MISSING.remove(active.getKey());
                            rise(player.serverLevel(), active.getKey(), entry.step(), target);
                        }
                    });
            }
        }
        MISSING.keySet().retainAll(inside);
    }
}
