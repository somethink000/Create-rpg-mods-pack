package net.expedition.route;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.expedition.Expedition;
import net.expedition.garrison.Garrisons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The author's play-through of the route (/expedition trial [step]): a stand-in player, in its chapter's armour with its
 * chapter's sword (ChapterGear), walks into every dungeon of the tour in turn as a player would - the approach scene,
 * the guardian rising, the dungeon's own boss waking, its spawner or altar if it has none in sight - and fights it:
 * full swings from the front and from behind, through the game's own attack (the bosses' shields, stances and
 * immunities, BossHits). Checked: is the boss there and on its dungeon, did a boss bar come, does the land
 * fit (an ocean dungeon at sea), how long the sword takes to kill it, how hard it hits back. -> trial.tsv
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class Trial {
    private Trial() {
    }

    private static final int ENTER_TICKS = 100;
    private static final int SEEK_TICKS = 100;
    private static final int SWINGS = 20;
    private static final int SWING_TICKS = 12;
    /** At most this long beside the boss for it to take the stand-in for its foe, then this long for it to wake. */
    private static final int PROVOKE_TICKS = 200;
    private static final int WAKE_TICKS = 80;

    private enum Phase { PLACING, ENTERING, SEEKING, PROVOKING, FIGHTING }

    /** The stand-in: a server player with no client, its swing always charged. */
    private static final class Stand extends ServerPlayer {
        Stand(MinecraftServer server, ServerLevel level, GameProfile profile, net.minecraft.server.level.ClientInformation info) {
            super(server, level, profile, info);
        }

        void charge() {
            this.attackStrengthTicker = 1000;
        }
    }

    private static MinecraftServer server;
    private static List<RouteSteps.Entry> stops;
    private static StringBuilder out;
    private static int index;
    private static Phase phase;
    private static long until;
    private static Stand stand;
    private static EmbeddedChannel channel;
    private static RouteData.Target target;

    /** The place the trial is at now (BossLair wakes its boss as for a player's step), or null. */
    static RouteData.Target current() {
        return stand == null ? null : target;
    }
    private static LivingEntity boss;
    private static String found;
    private static boolean sought;
    private static int swing;
    private static float front, back, taken, takenMax;
    private static int frontHits, backHits;
    private static long provokedUntil;
    private static boolean provoked;
    private static final Set<String> BARS = new LinkedHashSet<>();
    /** Every kind of packet the stand-in got this step (logged when no bar came, to tell a silent server from a missed bar). */
    private static final java.util.Map<String, Integer> SEEN = new java.util.TreeMap<>();

    static boolean isStandIn(ServerPlayer player) {
        return stand != null && player == stand;
    }

    /** The kill time a chapter's boss should take with its chapter's sword, in seconds. */
    static double targetSeconds(int act) {
        return 30 + 15 * (act - 1);
    }

    static Item sword(int act) {
        return act <= 2 ? Items.IRON_SWORD : act <= 4 ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD;
    }

    static List<Item> armor(int act) {
        return act <= 2 ? List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)
            : act <= 4 ? List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)
            : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
    }

    public static void start(MinecraftServer started, String only) {
        if (stops != null) {
            Expedition.LOG.warn("Trial: already running");
            return;
        }
        server = started;
        stops = Tour.stops().stream().filter(entry -> only.isEmpty() || Route.id(entry).equals(only)).toList();
        out = new StringBuilder("step\tact\tboss\tfound\tin_dungeon\tbiome\thome\twater\tbars\thealth\tfront_hit\tback_hit\tseconds\ttarget\ttaken_max\ttaken_per_s\tverdict\tarmor\tformula_hit\tstate\n");
        index = -1;
        phase = Phase.PLACING;
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "trial-stand-in"), false);
        stand = new Stand(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(connection);
        // As a modded client would have it: every mod's channels open (bars, scenes and the like come as payloads).
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        // An author's walk, not a new player's start: no prologue, no chapter openings.
        net.minecraft.nbt.CompoundTag seen = Route.persisted(stand);
        seen.putInt(Route.SEEN_ACT_TAG, Acts.LAST);
        stand.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, seen);
        server.getPlayerList().placeNewPlayer(connection, stand, cookie);
        stand.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        next();
    }

    private static void next() {
        index++;
        BARS.clear();
        SEEN.clear();
        boss = null;
        found = "";
        sought = false;
        target = null;
        if (index >= stops.size()) {
            finish();
            return;
        }
        RouteSteps.Entry entry = stops.get(index);
        Expedition.LOG.info("Trial {}: placing ({}/{})", Route.id(entry), index + 1, stops.size());
        phase = Phase.PLACING;
        if (BossLair.applies(entry.step())) {
            BlockPos lair = BossLair.find(server.overworld(), entry.step(), Branches.middle(server, 0));
            RouteData.Target lairTarget = null;
            if (lair != null) {
                lairTarget = new RouteData.Target();
                lairTarget.dimension = server.overworld().dimension().location().toString();
                lairTarget.pos = lair;
                lairTarget.dungeonKey = BossLair.KEY + entry.step().boss().get() + "|" + lair.getX() + "," + lair.getZ();
            }
            placed(lairTarget);
            return;
        }
        Route.reserve(server, Route.id(entry), Trial::placed);
    }

    private static final List<long[]> HELD = new ArrayList<>();
    private static ServerLevel heldLevel;

    /** The chunks around a place held loaded (entities and all) while the stand-in is there. */
    private static void hold(ServerLevel level, BlockPos at) {
        release();
        heldLevel = level;
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
                HELD.add(new long[] {cx + dx, cz + dz});
            }
        }
    }

    private static void release() {
        if (heldLevel != null) {
            HELD.forEach(chunk -> heldLevel.setChunkForced((int) chunk[0], (int) chunk[1], false));
        }
        HELD.clear();
        heldLevel = null;
    }

    private static void finish() {
        release();
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("trial.tsv"), out);
        } catch (java.io.IOException e) {
            Expedition.LOG.error("Trial: can't write trial.tsv", e);
        }
        Expedition.LOG.info("Trial: done, {} dungeons -> trial.tsv", stops.size());
        server.getPlayerList().remove(stand);
        stops = null;
        stand = null;
    }

    private static ServerLevel levelOf(RouteData.Target at) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(at.dimension)));
        return level == null ? server.overworld() : level;
    }

    private static void placed(RouteData.Target placedTarget) {
        if (stops == null) {
            return;
        }
        RouteSteps.Entry entry = stops.get(index);
        if (placedTarget == null) {
            write(entry, "NOT PLACED");
            next();
            return;
        }
        target = placedTarget;
        // As Route.place marks it: a boss that rises when a player comes (BossLair wakes the trial's place too).
        if (entry.step().summon() || BossLair.applies(entry.step())) {
            target.summon = entry.step().boss().map(net.minecraft.resources.ResourceLocation::toString).orElse(null);
        }
        ServerLevel level = levelOf(target);
        int act = Acts.actOf(entry.step());
        List<Item> armor = armor(act);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(armor.get(0)));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(armor.get(1)));
        stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(armor.get(2)));
        stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(armor.get(3)));
        stand.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(sword(act)));
        // In at the door, as the tour does: the main hall, else above the place.
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        Shots.Path hall = dungeon == null ? null : Shots.inside(level, dungeon.start());
        Vec3 to = hall != null ? hall.from()
            : Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos).above(2));
        stand.teleportTo(level, to.x, to.y, to.z, 0, 0);
        hold(level, BlockPos.containing(to));
        Expedition.LOG.info("Trial {}: entering at {} ({} players online, stand-in removed: {})", Route.id(entry), BlockPos.containing(to).toShortString(),
            server.getPlayerList().getPlayerCount(), stand.isRemoved());
        phase = Phase.ENTERING;
        until = server.getTickCount() + ENTER_TICKS;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (stops == null || stand == null || index < 0 || index >= stops.size()) {
            return;
        }
        try {
            tick();
        } catch (RuntimeException e) {
            // A trial never takes the server down: it stops, with what it has.
            Expedition.LOG.error("Trial: stopped", e);
            finish();
        }
    }

    private static void tick() {
        // As a client's connection would: the stand-in ticks (its gear's attributes, the chunks and entities around it).
        stand.doTick();
        if (stand.isSpectator()) {
            stand.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }
        drainPackets();
        stand.setHealth(stand.getMaxHealth());
        stand.getFoodData().setFoodLevel(20);
        if (phase == Phase.PLACING || server.getTickCount() < until) {
            return;
        }
        RouteSteps.Entry entry = stops.get(index);
        try {
            switch (phase) {
                case ENTERING -> {
                    // The approach scene and the guardian (Route.arrive), then a moment for the dungeon's boss to wake.
                    Route.arrive(stand, entry, target);
                    phase = Phase.SEEKING;
                    until = server.getTickCount() + SEEK_TICKS;
                }
                case SEEKING -> seek(entry);
                case PROVOKING -> provoke();
                case FIGHTING -> fight(entry);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            Expedition.LOG.error("Trial {}: failed", Route.id(entry), e);
            write(entry, "ERROR " + e);
            next();
        }
    }

    /** Boss bars sent to the stand-in: vanilla's, or a mod's bar payload (the garrison's own bar is no boss's). */
    private static void drainPackets() {
        Object message;
        while ((message = channel.readOutbound()) != null) {
            String kind = message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p
                ? p.payload().type().id().toString() : message.getClass().getSimpleName();
            SEEN.merge(kind, 1, Integer::sum);
            // As a client does: acknowledge each chunk batch, or the server sends no more chunks (since 1.20.2) and the
            // stand-in never sees the boss - nor a mod's bar, which goes to the players tracking the boss.
            if (message instanceof net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket && stand != null && stand.connection != null) {
                stand.connection.chunkSender.onChunkBatchReceivedByClient(64);
            }
            if (message instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
                for (var inner : bundle.subPackets()) {
                    SEEN.merge("bundle:" + inner.getClass().getSimpleName(), 1, Integer::sum);
                }
            }
            if (message instanceof ClientboundBossEventPacket packet) {
                try {
                    var operation = ClientboundBossEventPacket.class.getDeclaredField("operation");
                    operation.setAccessible(true);
                    Object op = operation.get(packet);
                    for (var field : op.getClass().getDeclaredFields()) {
                        if (field.getType() == Component.class) {
                            field.setAccessible(true);
                            Component name = (Component) field.get(op);
                            String text = name.getString();
                            if (!(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key && key.getKey().startsWith("expedition.garrison"))) {
                                BARS.add(text);
                            }
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    BARS.add("?");
                }
            }
            // A mod's own bar (Twilight's, Cataclysm's, Block Factory's) comes as its payload.
            if (message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) {
                ResourceLocation type = custom.payload().type().id();
                if (type.getPath().contains("boss") || type.getPath().contains("bar")) {
                    BARS.add(type.toString());
                }
            }
            io.netty.util.ReferenceCountUtil.release(message);
        }
    }

    private static AABB area(ServerLevel level) {
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        return dungeon != null ? AABB.of(dungeon.box()).inflate(16) : new AABB(target.pos).inflate(64);
    }

    private static void seek(RouteSteps.Entry entry) {
        RouteStep step = entry.step();
        ServerLevel level = levelOf(target);
        ResourceLocation bossId = step.guardian().map(RouteStep.Guardian::entity).or(step::boss).orElse(null);
        if (bossId == null) {
            write(entry, "NO BOSS DEFINED");
            next();
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(bossId);
        String id = Route.id(entry);
        // The step's own guardian first: a garrison mob of the same kind is not the boss.
        var candidates = level.getEntitiesOfClass(LivingEntity.class, area(level), entity -> entity.isAlive()
            && (id.equals(Guardians.stepOf(entity)) || entity.getType() == type && entity.getPersistentData().getString(Garrisons.TAG).isEmpty()));
        boss = candidates.stream().filter(entity -> id.equals(Guardians.stepOf(entity))).findFirst().orElse(candidates.isEmpty() ? null : candidates.get(0));
        if (boss != null) {
            found = found.isEmpty() ? (Guardians.stepOf(boss) != null ? "guardian" : "own") : found;
        } else if (!sought) {
            // Not in sight: its spawner or altar, where a player would go.
            sought = true;
            // A spawner that is an entity (the Sephirot's: touched, it opens a dossier whose button calls spawn()).
            for (var spawner : level.getEntities((net.minecraft.world.entity.Entity) null, area(level),
                entity -> entity.getClass().getSuperclass() != null && entity.getClass().getSuperclass().getSimpleName().equals("BossSpawnerEntity"))) {
                try {
                    spawner.getClass().getMethod("spawn").invoke(spawner);
                    found = "spawner " + BuiltInRegistries.ENTITY_TYPE.getKey(spawner.getType()).getPath();
                } catch (ReflectiveOperationException e) {
                    Expedition.LOG.warn("Trial: can't start {}", spawner, e);
                }
            }
            if (!found.isEmpty()) {
                until = server.getTickCount() + SEEK_TICKS;
                return;
            }
            BlockPos spot = summoningBlock(level);
            if (spot != null) {
                found = "near " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(spot).getBlock()).getPath();
                // Above it, as a player walks in: the Knight Phantoms' spawner wakes only for a player over 2 blocks higher.
                stand.teleportTo(level, spot.getX() + 0.5, spot.getY() + 3, spot.getZ() + 2.5, 0, 0);
                hold(level, spot);
            } else if (step.summon() || BossLair.applies(step)) {
                // Rises when a player comes (BossLair): the stand-in is there; give the lair its moment.
                found = "lair";
            }
            until = server.getTickCount() + SEEK_TICKS;
            return;
        }
        if (boss == null) {
            Expedition.LOG.info("Trial {}: no {} in {}; around the place: {}", id, bossId, area(level),
                level.getEntitiesOfClass(LivingEntity.class, new AABB(target.pos).inflate(48), entity -> !(entity instanceof ServerPlayer)).stream()
                    .map(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath() + "@" + entity.blockPosition().toShortString()
                        + (entity.isAlive() ? "" : "(dead)")).limit(12).toList());
            if (found.isEmpty()) {
                // No spawner or altar found: what block entities the place has at all (a spawner under another name).
                java.util.Set<String> kinds = new java.util.TreeSet<>();
                AABB box = area(level);
                for (int cx = ((int) box.minX) >> 4; cx <= ((int) box.maxX) >> 4; cx++) {
                    for (int cz = ((int) box.minZ) >> 4; cz <= ((int) box.maxZ) >> 4; cz++) {
                        if (level.hasChunk(cx, cz)) {
                            level.getChunk(cx, cz).getBlockEntitiesPos().forEach(pos -> kinds.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()));
                        }
                    }
                }
                Expedition.LOG.info("Trial {}: block entities around: {}", id, kinds);
            }
            write(entry, "NO BOSS" + (found.isEmpty() ? "" : " (" + found + ")"));
            next();
            return;
        }
        swing = 0;
        front = back = taken = takenMax = 0;
        frontHits = backHits = 0;
        provoked = false;
        provokedUntil = server.getTickCount() + PROVOKE_TICKS;
        phase = Phase.PROVOKING;
        until = server.getTickCount() + 1;
    }

    /** Beside the boss until it takes the stand-in for its foe (a sleeping boss wakes then), and a moment to wake. */
    private static void provoke() {
        if (!boss.isAlive()) {
            phase = Phase.FIGHTING;
            return;
        }
        if (provoked) {
            phase = Phase.FIGHTING;
            until = server.getTickCount() + 1;
            return;
        }
        boolean targeted = boss instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == stand;
        if (targeted || server.getTickCount() >= provokedUntil) {
            provoked = true;
            found += targeted ? "" : " (never targeted)";
            until = server.getTickCount() + WAKE_TICKS;
            return;
        }
        Vec3 facing = Vec3.directionFromRotation(0, boss.getYRot());
        Vec3 at = boss.position().add(facing.scale(Math.max(3, boss.getBbWidth() / 2 + 3)));
        stand.teleportTo((ServerLevel) boss.level(), at.x, boss.getY(), at.z, 0, 0);
        stand.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
        if (boss instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == null) {
            // As a player hitting it would: a boss that waits to be struck (or to be looked at) is.
            mob.setLastHurtByPlayer(stand);
        }
        until = server.getTickCount() + 20;
    }

    /** A block that makes a boss: a spawner (Twilight's, Bosses of Mass Destruction's) or an altar (Cataclysm's). */
    private static BlockPos summoningBlock(ServerLevel level) {
        AABB area = area(level);
        BlockPos best = null;
        for (int cx = ((int) area.minX) >> 4; cx <= ((int) area.maxX) >> 4; cx++) {
            for (int cz = ((int) area.minZ) >> 4; cz <= ((int) area.maxZ) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockPos pos : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
                    if ((path.contains("boss") || path.contains("altar") || path.contains("summon")) && !path.contains("chest")) {
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static void fight(RouteSteps.Entry entry) {
        if (!boss.isAlive() || swing >= SWINGS) {
            report(entry);
            next();
            return;
        }
        ServerLevel level = (ServerLevel) boss.level();
        boolean behind = swing % 2 == 1;
        Vec3 facing = Vec3.directionFromRotation(0, boss.getYRot());
        double reach = Math.max(1.5, boss.getBbWidth() / 2 + 1.5);
        Vec3 at = boss.position().add(facing.scale(behind ? -reach : reach));
        stand.teleportTo(level, at.x, boss.getY(), at.z, 0, 0);
        stand.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
        stand.charge();
        boss.invulnerableTime = 0;
        float before = boss.getHealth() + boss.getAbsorptionAmount();
        stand.attack(boss);
        float dealt = Math.max(0, before - (boss.getHealth() + boss.getAbsorptionAmount()));
        if (behind) {
            back += dealt;
            backHits++;
        } else {
            front += dealt;
            frontHits++;
        }
        swing++;
        until = server.getTickCount() + SWING_TICKS;
    }

    /** What the bosses do to the stand-in: counted after armour, then taken away (it must live through the trial). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onStandHurt(LivingDamageEvent.Pre event) {
        if (stand != null && event.getEntity() == stand) {
            if (phase == Phase.FIGHTING || phase == Phase.PROVOKING) {
                taken += event.getNewDamage();
                takenMax = Math.max(takenMax, event.getNewDamage());
            }
            event.setNewDamage(0);
        }
    }

    private static void report(RouteSteps.Entry entry) {
        RouteStep step = entry.step();
        int act = Acts.actOf(step);
        ServerLevel level = levelOf(target);
        List<String> problems = new ArrayList<>();
        // The land's biome: at the surface over the dungeon (its start may lie in a cave).
        BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, target.pos);
        var biome = level.getBiome(surface).unwrapKey().map(key -> key.location().toString()).orElse("?");
        boolean home = step.homeBiomes().isEmpty() || step.homeBiomes().stream().anyMatch(id -> id.toString().equals(biome));
        // By the terrain before the dungeon, as the placer judges it (a ship or a sunken city covers its own water).
        int samples = 9;
        int water = RoutePlacer.waterShare(level, new net.minecraft.world.level.levelgen.structure.BoundingBox(
            target.pos.getX() - 32, 0, target.pos.getZ() - 32, target.pos.getX() + 32, 0, target.pos.getZ() + 32));
        if (!home) {
            problems.add("BIOME");
        }
        if (step.homeBiomes().stream().anyMatch(id -> id.getPath().contains("ocean")) && water * 2 < samples) {
            problems.add("DRY");
        }
        Garrisons.Dungeon dungeon = Prologue.lairDungeon(level, target);
        boolean inDungeon = dungeon == null || dungeon.box().inflatedBy(4).isInside(boss.blockPosition());
        if (!inDungeon) {
            problems.add("OUTSIDE");
        }
        if (BARS.isEmpty()) {
            problems.add("NO BAR");
            Expedition.LOG.info("Trial {}: no bar; the stand-in got {}", Route.id(entry), SEEN);
        }
        float frontHit = frontHits == 0 ? 0 : front / frontHits;
        float backHit = backHits == 0 ? 0 : back / backHits;
        float best = Math.max(frontHit, backHit);
        float max = boss.getMaxHealth();
        double seconds = best > 0.01 ? max / best / ChapterGear.SWORD_SPEED : Double.POSITIVE_INFINITY;
        double goal = targetSeconds(act);
        if (best <= 0.01) {
            problems.add("IMMUNE");
        } else if (seconds > goal * 2) {
            problems.add("TANKY");
        } else if (seconds < goal / 3) {
            problems.add("SOFT");
        }
        double fightSeconds = Math.max(1, (swing * SWING_TICKS + WAKE_TICKS) / 20.0);
        float playerHealth = stand.getMaxHealth();
        if (takenMax >= playerHealth * 0.5f) {
            problems.add("ONE-TWO-SHOT");
        }
        if (boss.isAlive()) {
            boss.setHealth(max);
        }
        String verdict = problems.isEmpty() ? "OK" : String.join(" ", problems);
        out.append(Route.id(entry)).append('\t').append(act).append('\t').append(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType())).append('\t')
            .append(found).append('\t').append(inDungeon ? 1 : 0).append('\t').append(biome).append('\t').append(home ? 1 : 0).append('\t')
            .append(water * 100 / samples).append('\t').append(String.join("|", BARS)).append('\t').append((int) max).append('\t')
            .append(String.format(Locale.ROOT, "%.1f\t%.1f\t", frontHit, backHit)).append(Double.isInfinite(seconds) ? "inf" : String.valueOf((int) seconds))
            .append('\t').append((int) goal).append('\t').append(String.format(Locale.ROOT, "%.1f\t%.1f\t", takenMax, taken / fightSeconds)).append(verdict)
            .append('\t').append(boss.getArmorValue()).append('\t')
            // What the sword would do through its armour alone (for a boss hurt only by its mechanic: the estimate).
            .append(String.format(Locale.ROOT, "%.1f", ChapterGear.afterArmor((float) stand.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                boss.getArmorValue(), (float) boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS))))
            .append('\t').append(state(boss)).append('\n');
        Expedition.LOG.info("Trial {}: {} ({}) hp {} hit {}/{} -> {} s (target {}), hits back {} max: {} [{}]", Route.id(entry),
            BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()), found, (int) max, String.format(Locale.ROOT, "%.1f", frontHit),
            String.format(Locale.ROOT, "%.1f", backHit), Double.isInfinite(seconds) ? "inf" : (int) seconds, (int) goal,
            String.format(Locale.ROOT, "%.1f", takenMax), verdict, state(boss));
    }

    /** The boss's own saved state (what its mod adds to a mob's data: awake, stance, shields), for reading an immunity. */
    private static String state(LivingEntity entity) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        entity.saveWithoutId(tag);
        net.minecraft.nbt.CompoundTag plain = new net.minecraft.nbt.CompoundTag();
        new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE, entity.level()).saveWithoutId(plain);
        StringBuilder text = new StringBuilder();
        for (String key : tag.getAllKeys()) {
            var value = tag.get(key);
            if (!plain.contains(key) && value != null && !(value instanceof net.minecraft.nbt.CompoundTag) && !(value instanceof net.minecraft.nbt.ListTag)
                && value.getAsString().length() <= 24 && !key.equals("Paper.SpawnReason")) {
                text.append(key).append('=').append(value.getAsString()).append(' ');
            }
        }
        return text.toString().trim();
    }

    private static void write(RouteSteps.Entry entry, String verdict) {
        out.append(Route.id(entry)).append('\t').append(Acts.actOf(entry.step())).append("\t\t").append(found).append("\t\t\t\t\t")
            .append(String.join("|", BARS)).append("\t\t\t\t\t\t\t\t").append(verdict).append('\n');
        Expedition.LOG.info("Trial {}: {}", Route.id(entry), verdict);
    }
}
