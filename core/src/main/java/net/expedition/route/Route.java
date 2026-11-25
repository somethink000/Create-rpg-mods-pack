package net.expedition.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.expedition.Expedition;
import net.expedition.ExpeditionRegistry;
import net.expedition.garrison.Garrisons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The expedition story ("The Stitched World"): acts of route steps (see {@link Acts}). When an act opens, all its steps'
 * dungeons are placed one after another far from spawn (see {@link RoutePlacer}); the fireseed shows the way to them.
 * Killing a dungeon's boss completes the step for every journal of the players nearby, so friends can play the story
 * together, and marks the place on every player's map (RouteMarkers). Three quarters of an act's steps done, the
 * next act opens. Progress shows as the advancement tree "The Stitched
 * World", scenes play at act openings and key bosses' falls, and the guide book's story chapters follow the acts.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class Route {
    private Route() {
    }

    /** Players this close to the dungeon or boss when it falls get the step credited. */
    static final int CREDIT_RADIUS = 96;
    private static final String JOURNAL_TAG = Expedition.MOD_ID + ":journal";
    /** Ticks after joining before the first act opens: at once (its scene waits on the client until the world is shown). */
    private static final int FIRST_ACT_DELAY = 1;

    private static final String BRANCH_TAG = Expedition.MOD_ID + ":branch";
    private static final String GIVEN_TAG = Expedition.MOD_ID + ":given";
    static final String SEEN_ACT_TAG = Expedition.MOD_ID + ":seen_act";

    /**
     * The story journal the player plays in: their branch's (Branches). Everyone on a branch shares its journal: its
     * chapter, its dungeons and what is done there. What each player saw or was given is their own (seen, giveOnce).
     */
    public static UUID journalOf(ServerPlayer player) {
        return branchJournal(branchOf(player));
    }

    static UUID branchJournal(int branch) {
        return UUID.nameUUIDFromBytes(("expedition:branch:" + branch).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The player's branch: 0 (the one around spawn) until they throw a Sprout of the Tree. */
    public static int branchOf(ServerPlayer player) {
        return persisted(player).getInt(BRANCH_TAG);
    }

    /** Test hook: each test's players on a branch of their own (tests run side by side; a branch shares its journal). */
    public static void setBranchForTests(ServerPlayer player, int branch) {
        setBranch(player, branch);
    }

    static void setBranch(ServerPlayer player, int branch) {
        CompoundTag tag = persisted(player);
        tag.putInt(BRANCH_TAG, branch);
        tag.remove(SEEN_ACT_TAG); // a new branch: its chapters are heard anew
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    /** True the first time for this player and key (a book given, a scene seen); kept with the player, death-proof. */
    static boolean giveOnce(ServerPlayer player, String key) {
        CompoundTag tag = persisted(player);
        net.minecraft.nbt.ListTag given = tag.getList(GIVEN_TAG, net.minecraft.nbt.Tag.TAG_STRING);
        if (given.stream().anyMatch(entry -> entry.getAsString().equals(key))) {
            return false;
        }
        given.add(net.minecraft.nbt.StringTag.valueOf(key));
        tag.put(GIVEN_TAG, given);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
        return true;
    }

    static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    static Component title(RouteSteps.Entry entry) {
        return Component.translatable(entry.step().langKey(entry.id()) + ".title");
    }

    static String id(RouteSteps.Entry entry) {
        return entry.id().getPath();
    }

    /** A step of a dimension merged into the Overworld (Nether, End...) is placed in the Overworld, in that realm. */
    public static ServerLevel levelFor(MinecraftServer server, RouteStep step) {
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, step.dimension());
        return net.expedition.world.realm.RealmWorld.isMerged(key) ? server.overworld() : server.getLevel(key);
    }

    // ---- Acts --------------------------------------------------------------------------------------------------

    /** Opens an act for a journal (a branch): its players hear it, and its steps' dungeons are queued for placement. */
    public static void startAct(ServerPlayer player, UUID journal, int act) {
        RouteData data = RouteData.get(player.getServer());
        RouteData.Progress progress = data.progress(journal);
        progress.act = Math.max(progress.act, act);
        data.setDirty();
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (journalOf(other).equals(journal)) {
                catchUp(other, progress);
            }
        }
        openSteps(player, journal);
    }

    /**
     * The player hears the chapter their branch is at, if they haven't yet (a player joining a branch others began, or
     * offline when it opened): its advancements and its opening; chapter I of the spawn branch opens with the prologue.
     */
    static void catchUp(ServerPlayer player, RouteData.Progress progress) {
        CompoundTag tag = persisted(player);
        int seen = tag.getInt(SEEN_ACT_TAG);
        if (seen >= progress.act) {
            return;
        }
        for (int act = seen + 1; act <= progress.act; act++) {
            StoryAdvancements.award(player, "story/act_" + act);
        }
        tag.putInt(SEEN_ACT_TAG, progress.act);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
        Acts.Act definition = Acts.get(progress.act);
        if (definition == null) {
            return;
        }
        // Act I opens with the prologue (the stitched world's story over its realms, then the Tree at the grave) on the
        // spawn branch; a client that can't show it just hears act I. Every later chapter is heard while the player plays.
        if (progress.act != 1 || progress.branch != 0 || !Prologue.start(player)) {
            Cutscenes.play(player, player.blockPosition(), "expedition.story.act." + progress.act, definition.intro(), true);
        }
    }

    // ---- Placing the dungeons: every chapter's at once -------------------------------------------------------------

    /** Steps waiting to be placed, per journal: one is placed at a time. */
    private static final Map<UUID, Deque<RouteSteps.Entry>> QUEUE = new HashMap<>();
    private static final Set<UUID> PLACING = new HashSet<>();
    /** The player each journal's placements are for (their map gets the marks). */
    private static final Map<UUID, UUID> OWNER = new HashMap<>();

    /**
     * Queues every step of the route, of any chapter, that has neither a dungeon in the world nor is done: the whole
     * world stands from the start; the chapters only order the story (and the fireseed leads to the easiest first).
     */
    static void openSteps(ServerPlayer player, UUID journal) {
        RouteData.Progress progress = RouteData.get(player.getServer()).progress(journal);
        Deque<RouteSteps.Entry> queue = QUEUE.computeIfAbsent(journal, id -> new ArrayDeque<>());
        OWNER.put(journal, player.getUUID());
        for (RouteSteps.Entry entry : RouteSteps.all()) {
            String id = id(entry);
            boolean placeable = !entry.step().structures().isEmpty() || BossLair.applies(entry.step());
            if (placeable && !progress.done.contains(id) && !progress.active.containsKey(id)
                && !queue.contains(entry)) {
                queue.add(entry);
            }
        }
        pump(player.getServer(), journal);
    }

    private static void pump(MinecraftServer server, UUID journal) {
        Deque<RouteSteps.Entry> queue = QUEUE.get(journal);
        if (queue == null || queue.isEmpty() || PLACING.contains(journal)) {
            return;
        }
        RouteSteps.Entry entry = queue.poll();
        RouteStep step = entry.step();
        RouteData.Progress progress = RouteData.get(server).progress(journal);
        // Dungeons placed ahead for the prologue stand around spawn: only the spawn branch takes them.
        RouteData.Target ahead = progress.branch == 0 ? RouteData.get(server).reserved.remove(id(entry)) : null;
        if (ahead != null) {
            // Placed ahead of its act for the prologue: this journal's dungeon is that one.
            placed(server, journal, entry, ahead.dimension, ahead.pos, ahead.dungeonKey);
            pump(server, journal);
            return;
        }
        ServerLevel level = levelFor(server, step);
        if (level == null) {
            Expedition.LOG.error("Route step {}: unknown dimension {}", entry.id(), step.dimension());
            pump(server, journal);
            return;
        }
        BlockPos spawn = Branches.middle(server, progress.branch);
        if (BossLair.applies(step)) {
            BlockPos lair = BossLair.find(server.overworld(), step, spawn);
            if (lair != null) {
                placed(server, journal, entry, server.overworld().dimension().location().toString(), lair,
                    BossLair.KEY + step.boss().get() + "|" + lair.getX() + "," + lair.getZ());
            }
            pump(server, journal);
            return;
        }
        PLACING.add(journal);
        var realm = net.expedition.world.realm.RealmWorld.realmFor(level, step.dimension());
        RoutePlacer.request(level, step, spawn, realm, progress.branch, result -> {
            PLACING.remove(journal);
            if (result == null) {
                Expedition.LOG.warn("Story: step {} could not be placed now; it is tried again when the player next joins", entry.id());
            } else {
                placed(server, journal, entry, result.dimension(), result.pos(), result.dungeonKey());
            }
            pump(server, journal);
        });
    }

    /** Callbacks waiting for a dungeon being placed ahead of its act, by step id. */
    private static final Map<String, List<java.util.function.Consumer<RouteData.Target>>> AHEAD = new HashMap<>();

    /**
     * A step's dungeon standing in the world, for the prologue to film: one some journal already has, one placed ahead
     * before, or else placed now and kept for the first journal that opens the step. Null to {@code done} if it can't be.
     */
    static void reserve(MinecraftServer server, String stepId, java.util.function.Consumer<RouteData.Target> done) {
        RouteData data = RouteData.get(server);
        RouteData.Target known = data.reserved.get(stepId);
        // The prologue is the spawn branch's: its journal's dungeon, if it stands already.
        known = known != null ? known : data.progress(branchJournal(0)).active.get(stepId);
        RouteSteps.Entry entry = RouteSteps.all().stream().filter(e -> id(e).equals(stepId)).findFirst().orElse(null);
        ServerLevel level = entry == null ? null : levelFor(server, entry.step());
        if (known != null || level == null || entry.step().structures().isEmpty()) {
            done.accept(known);
            return;
        }
        List<java.util.function.Consumer<RouteData.Target>> waiting = AHEAD.get(stepId);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        AHEAD.put(stepId, new ArrayList<>(List.of(done)));
        var realm = net.expedition.world.realm.RealmWorld.realmFor(level, entry.step().dimension());
        RoutePlacer.request(level, entry.step(), server.overworld().getSharedSpawnPos(), realm, result -> {
            RouteData.Target target = null;
            if (result != null) {
                target = new RouteData.Target();
                target.dimension = result.dimension();
                target.pos = result.pos();
                target.dungeonKey = result.dungeonKey();
                data.reserved.put(stepId, target);
                data.setDirty();
            }
            RouteData.Target placed = target;
            AHEAD.remove(stepId).forEach(callback -> callback.accept(placed));
        });
    }

    private static void placed(MinecraftServer server, UUID journal, RouteSteps.Entry entry, String dimension, BlockPos pos, String dungeonKey) {
        RouteData data = RouteData.get(server);
        RouteData.Progress progress = data.progress(journal);
        if (progress.done.contains(id(entry))) {
            return;
        }
        RouteData.Target target = new RouteData.Target();
        target.dimension = dimension;
        target.pos = pos;
        target.dungeonKey = dungeonKey;
        if (entry.step().summon() || BossLair.applies(entry.step())) {
            target.summon = entry.step().boss().map(ResourceLocation::toString).orElse(null);
        }
        progress.active.put(id(entry), target);
        data.setDirty();
        // Not marked on the map: the way there is found by the fireseed (GuidingSeed), progress shows in advancements.
    }

    // ---- Completing steps ---------------------------------------------------------------------------------------

    /** Completes, for every journal of the given players, the opened steps the test accepts (step, its target or null). */
    static void complete(MinecraftServer server, List<ServerPlayer> players, BlockPos where, BiPredicate<RouteSteps.Entry, RouteData.Target> matches) {
        RouteData data = RouteData.get(server);
        for (ServerPlayer player : players) {
            UUID journal = journalOf(player);
            RouteData.Progress progress = data.progress(journal);
            for (RouteSteps.Entry entry : RouteSteps.all()) {
                String id = id(entry);
                RouteData.Target target = progress.active.get(id);
                if (progress.done.contains(id) || !matches.test(entry, target)) {
                    continue;
                }
                progress.done.add(id);
                progress.active.remove(id);
                data.setDirty();
                if (target != null) {
                    ServerLevel markerLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(target.dimension)));
                    if (markerLevel != null) {
                        RouteMarkers.clear(markerLevel, journal, id);
                        if (!target.dungeonKey.isEmpty()) {
                            Garrisons.bossFell(markerLevel, target.dungeonKey, target.pos);
                        }
                    }
                }
                StoryAdvancements.award(player, "story/step/" + id);
                int chapter = Acts.actOf(entry.step());
                // Where the boss fell: on every player's map from now on, for whoever comes this way later.
                RouteData.Target fallen = target;
                if (fallen == null && where != null) {
                    fallen = new RouteData.Target();
                    fallen.dimension = player.level().dimension().location().toString();
                    fallen.pos = where;
                    fallen.dungeonKey = "";
                }
                if (fallen != null && !progress.fallen.containsKey(id)) {
                    progress.fallen.put(id, fallen);
                    RouteMarkers.markFallen(server, id, fallen);
                }
                // Experience to spend (Apotheosis's tables, an anvil): Create's nuggets, more for later chapters.
                BlockPos drop = where != null ? where : player.blockPosition();
                net.minecraft.world.item.ItemStack experience = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:experience_nugget")), 8 * chapter);
                if (player.blockPosition().distSqr(drop) < 64 * 64) {
                    player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.serverLevel(), drop.getX() + 0.5, drop.getY() + 1,
                        drop.getZ() + 0.5, experience));
                } else {
                    player.getInventory().placeItemBackInInventory(experience);
                }
                player.displayClientMessage(Component.translatable("expedition.story.step_done", title(entry).copy().withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GREEN), false);
                Acts.Act act = Acts.get(Acts.actOf(entry.step()));
                BlockPos scene = where != null ? where : target != null ? target.pos : player.blockPosition();
                if (act != null && act.keystones().containsKey(id)) {
                    Cutscenes.play(player, scene, "expedition.story.outro." + id, act.keystones().get(id), false);
                } else if (!entry.step().fallSpeakers().isEmpty()) {
                    Cutscenes.play(player, scene, "expedition.story.fall." + id, entry.step().fallSpeakers(), false);
                }
                checkFinale(player, progress);
                checkActDone(player, journal, progress, chapter);
            }
        }
    }

    /** The share of an act's steps that opens the next one. */
    static final double ACT_SHARE = 0.75;

    /** Steps of an act that must be done for the next to open: three quarters, rounded up. */
    static int needed(int act) {
        long steps = RouteSteps.all().stream().filter(entry -> Acts.actOf(entry.step()) == act).count();
        return (int) Math.ceil(steps * ACT_SHARE);
    }

    /** Enough of the journal's act done: the next act opens (its dungeons placed, its opening heard by all its players). */
    static void checkActDone(ServerPlayer player, UUID journal, RouteData.Progress progress, int act) {
        if (act != progress.act || act >= Acts.LAST) {
            return;
        }
        long done = RouteSteps.all().stream().filter(entry -> Acts.actOf(entry.step()) == act && progress.done.contains(id(entry))).count();
        if (done >= needed(act)) {
            startAct(player, journal, act + 1);
        }
    }

    private static void checkFinale(ServerPlayer player, RouteData.Progress progress) {
        Acts.Act last = Acts.get(Acts.LAST);
        if (progress.finished || progress.act < Acts.LAST || !progress.done.containsAll(last.keystones().keySet())) {
            return;
        }
        progress.finished = true;
        StoryAdvancements.award(player, "story/finale");
        DELAYED.add(new Delayed(player.getServer().getTickCount() + 200, player.getUUID(),
            p -> Cutscenes.play(p, p.blockPosition(), "expedition.story.finale", Acts.FINALE, true)));
    }

    private static List<ServerPlayer> playersNear(ServerLevel level, AABB area) {
        return level.players().stream().filter(player -> area.contains(player.position())).toList();
    }

    /** Called by the garrisons when a dungeon is cleared. */
    public static void onDungeonCleared(ServerLevel level, String dungeonKey, BoundingBox box) {
        AABB area = box == null ? null : AABB.of(box).inflate(CREDIT_RADIUS);
        List<ServerPlayer> players = area == null ? List.copyOf(level.players()) : playersNear(level, area);
        BlockPos center = box == null ? null : box.getCenter();
        // A guarded dungeon is done when its guardian falls, not its garrison.
        complete(level.getServer(), players, center, (entry, target) -> target != null && dungeonKey.equals(target.dungeonKey)
            && entry.step().guardian().isEmpty());
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation type = EntityType.getKey(entity.getType());
        List<ServerPlayer> players = playersNear(level, entity.getBoundingBox().inflate(CREDIT_RADIUS));
        if (!players.isEmpty()) {
            String guarded = Guardians.stepOf(entity);
            complete(level.getServer(), players, entity.blockPosition(), (entry, target) -> guarded != null ? id(entry).equals(guarded)
                : entry.step().boss().map(type::equals).orElse(false));
        }
    }

    // ---- Garrisons ask about story dungeons ----------------------------------------------------------------------

    private static RouteSteps.Entry entryFor(MinecraftServer server, String dungeonKey) {
        // Placed ahead for a branch (the prologue, the author's tour), not opened by any journal yet.
        for (Map.Entry<String, RouteData.Target> reserved : RouteData.get(server).reserved.entrySet()) {
            if (dungeonKey.equals(reserved.getValue().dungeonKey)) {
                return RouteSteps.all().stream().filter(entry -> id(entry).equals(reserved.getKey())).findFirst().orElse(null);
            }
        }
        for (RouteData.Progress progress : RouteData.get(server).all().values()) {
            for (Map.Entry<String, RouteData.Target> active : progress.active.entrySet()) {
                if (dungeonKey.equals(active.getValue().dungeonKey)) {
                    return RouteSteps.all().stream().filter(entry -> id(entry).equals(active.getKey())).findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    /** A story dungeon with a boss or a guardian: held section by section, cleared when the boss falls (Garrisons). */
    public static boolean isBossDungeon(MinecraftServer server, String dungeonKey) {
        RouteSteps.Entry entry = entryFor(server, dungeonKey);
        return entry != null && (entry.step().boss().isPresent() || entry.step().guardian().isPresent());
    }

    /** The chapter of the story dungeon with this key, or 0 if it is no story dungeon. */
    public static int actOfDungeon(MinecraftServer server, String dungeonKey) {
        RouteSteps.Entry entry = entryFor(server, dungeonKey);
        return entry == null ? 0 : Acts.actOf(entry.step());
    }

    /** The monsters this story dungeon's garrison is made of (its step's list); empty for other dungeons. */
    public static List<String> garrisonFor(MinecraftServer server, String dungeonKey) {
        RouteSteps.Entry entry = entryFor(server, dungeonKey);
        return entry == null ? List.of() : entry.step().garrison().stream().map(ResourceLocation::toString).toList();
    }

    /** The story step title of this dungeon, or null if it isn't a story dungeon. */
    public static Component titleFor(MinecraftServer server, String dungeonKey) {
        RouteSteps.Entry entry = entryFor(server, dungeonKey);
        return entry == null ? null : title(entry);
    }

    private static final net.minecraft.tags.TagKey<Item> TROPHIES = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "trophies"));

    /** Trophies the garrison of this story dungeon carries (its step's list); empty for other dungeons. */
    public static List<Item> trophiesFor(MinecraftServer server, String dungeonKey) {
        RouteSteps.Entry entry = entryFor(server, dungeonKey);
        return entry == null ? List.of() : entry.step().loot().stream().map(BuiltInRegistries.ITEM::get).filter(item -> item.builtInRegistryHolder().is(TROPHIES)).toList();
    }

    // ---- Joining ------------------------------------------------------------------------------------------------

    private record Delayed(long tick, UUID player, java.util.function.Consumer<ServerPlayer> action) {
    }

    private static final List<Delayed> DELAYED = new ArrayList<>();

    /** Fireseeds given on the first join (more are crafted: seeds, redstone, blaze powder). */
    static final int GUIDING_SEEDS = 3;

    /** On join: the guide book and fireseeds once; the first act opens after a moment; opened acts' missing dungeons get placed. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // One story for everyone (a new run of a dungeon is its NG+): a player on a branch of the old Sprout comes back.
        if (branchOf(player) != 0) {
            setBranch(player, 0);
        }
        UUID journal = journalOf(player);
        player.getInventory().clearOrCountMatchingItems(stack -> stack.has(ExpeditionRegistry.JOURNAL_ID.get()), -1, player.inventoryMenu.getCraftSlots());
        if (giveOnce(player, "guide_book")) {
            StoryBook.give(player);
        }
        if (giveOnce(player, "guiding_seed")) {
            player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(StoryItems.GUIDING_SEED.get(), GUIDING_SEEDS));
        }
        // The branch's first player opens chapter I; whoever joins later hears where the branch is (catchUp), each once.
        DELAYED.add(new Delayed(player.getServer().getTickCount() + FIRST_ACT_DELAY, player.getUUID(), p -> {
            RouteData.Progress now = RouteData.get(p.getServer()).progress(journalOf(p));
            RouteMarkers.markAll(p, now);
            if (now.act == 0) {
                startAct(p, journalOf(p), 1);
            } else {
                catchUp(p, now);
                openSteps(p, journalOf(p));
            }
        }));
    }

    /**
     * A player this close to a story dungeon for the first time hears the Tree on it (voice and subtitles only: no camera,
     * no black screen, the player keeps moving - a held, filmed player was killed by whatever stood at the door).
     */
    /** Arrived: within this of the dungeon's frame (all three axes), or of a lair's place (a boss without a dungeon). */
    static final int ARRIVE_MARGIN = 6;
    static final int LAIR_RADIUS = 24;
    /** Only places this near (horizontally) are looked at closer. */
    private static final int ARRIVE_LOOK = 256;
    /** The dungeons' frames, by dungeon key (a structure doesn't move). */
    private static final Map<String, BoundingBox> FRAMES = new HashMap<>();

    /** The player is at the place itself: in (or at the door of) its dungeon, not just over it or near it. */
    static boolean arrived(ServerPlayer player, RouteData.Target target) {
        if (player.blockPosition().distToCenterSqr(target.pos.getX(), player.getY(), target.pos.getZ()) > ARRIVE_LOOK * ARRIVE_LOOK) {
            return false;
        }
        if (target.dungeonKey.startsWith(BossLair.KEY)) {
            return player.blockPosition().distSqr(target.pos) < LAIR_RADIUS * LAIR_RADIUS;
        }
        BoundingBox frame = FRAMES.computeIfAbsent(target.dungeonKey, key -> {
            Garrisons.Dungeon dungeon = Prologue.lairDungeon(player.serverLevel(), target);
            return dungeon == null ? null : dungeon.box();
        });
        return frame != null && frame.inflatedBy(ARRIVE_MARGIN).isInside(player.blockPosition());
    }

    /** The player at a step's dungeon: its guardian rises (or the dungeon's own boss becomes it) and the approach scene is heard. */
    static void arrive(ServerPlayer player, RouteSteps.Entry entry, RouteData.Target target) {
        String id = id(entry);
        Guardians.rise(player.serverLevel(), id, entry.step(), target);
        if (!entry.step().arriveSpeakers().isEmpty()) {
            Cutscenes.play(player, target.pos, "expedition.story.arrive." + id, entry.step().arriveSpeakers(), false);
        }
    }

    private static void checkArrivals(MinecraftServer server) {
        RouteData data = RouteData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RouteData.Progress progress = data.progress(journalOf(player));
            String here = player.level().dimension().location().toString();
            for (Map.Entry<String, RouteData.Target> active : progress.active.entrySet()) {
                RouteData.Target target = active.getValue();
                // Each player's own scene, once per player (two players on one branch each hear it at the door).
                if (!target.dimension.equals(here) || !arrived(player, target) || !giveOnce(player, "arrive:" + progress.branch + ":" + active.getKey())) {
                    continue;
                }
                RouteSteps.all().stream().filter(entry -> id(entry).equals(active.getKey())).findFirst().ifPresent(entry -> arrive(player, entry, target));
                return;
            }
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            checkArrivals(event.getServer());
        }
        if (DELAYED.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        for (Delayed delayed : List.copyOf(DELAYED)) {
            if (delayed.tick() <= now) {
                DELAYED.remove(delayed);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(delayed.player());
                if (player != null) {
                    delayed.action().accept(player);
                }
            }
        }
    }

    /** Test hook: forget placements in flight (a test world starts over). */
    public static void resetForTests() {
        QUEUE.clear();
        PLACING.clear();
        OWNER.clear();
        DELAYED.clear();
    }
}
