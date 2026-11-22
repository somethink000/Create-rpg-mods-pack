package net.expedition.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.expedition.Expedition;
import net.expedition.net.CutscenePayload;
import net.expedition.garrison.Garrisons;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * The prologue, played when a player first comes into the world: the story of the stitched world told over the lairs
 * of its guardians - a temple of the Overworld, a Nether arena, the Twilight lich's tower, the Aether's valkyrie temple,
 * the End's castle - and last the withered Tree at spawn: the camera comes down from its crown to the player's grave
 * while a spark of the Tree flies the same way, and the Tree tells the player what it raised them for. They rise from
 * the grave when it ends.
 * <p>The lairs are the real dungeons of later acts: placed ahead now (Route.reserve, behind a loading screen) and kept
 * for the player's journal when their act opens. Each shot looks at the middle of its lair from close by (only that
 * much has to load); a lair that came out underground is left out and its words go to the next shot. For each shot the
 * player is moved there as a spectator (the client gets that place's chunks), the client waits for them, flies the
 * camera along the shot's path (CutsceneClient) and reports its end; then the next shot. At the end, or when the
 * player skips, they are put back where they stood, in their game mode; also on their next login if the server went
 * down meanwhile.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class Prologue {
    private Prologue() {
    }

    /** A shot: the narration (lang expedition.story.prologue.<place>.N, its speakers) and the route step whose lair it shows. */
    public record Shot(String place, String speakers, String step) {
    }

    static final List<Shot> SHOTS = List.of(new Shot("overworld", "T", "05_mossy_temple"), new Shot("nether", "T", "06_underworld_arena"),
        new Shot("twilight", "T", "50_tf_lich_tower"), new Shot("aether", "T", "58_silver_dungeon"), new Shot("end", "T", "18_ender_dragon"),
        new Shot("seam", "T", "33_umvuthana_grove"), new Shot("camp", "T", null));

    private static final TicketType<ChunkPos> TICKET = TicketType.create("expedition_prologue", java.util.Comparator.comparingLong(ChunkPos::toLong), 20 * 300);
    private static final String RESTORE = "expedition_prologue_restore";
    /** Waiting for the lairs to be placed at most this long; a shot the client doesn't report ends after the other. */
    private static final int LOAD_TICKS = 20 * 240;
    private static final int SHOT_TICKS = 20 * 90;

    private record Placed(Shot shot, List<String> lines, BlockPos focus, Vec3 from, Vec3 to) {
    }

    private static final class Run {
        /** The lairs found so far, by step; a step that could not be placed maps to null. */
        final Map<String, RouteData.Target> lairs = new HashMap<>();
        final List<Placed> shots = new ArrayList<>();
        int index = -1;
        long deadline;
        long shotStart;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    /** Starts the prologue for a player (act I's opening); false if their client can't show it. */
    /** Is the player in the prologue (carried around the lairs as a spectator)? */
    public static boolean running(ServerPlayer player) {
        return RUNS.containsKey(player.getUUID());
    }

    public static boolean start(ServerPlayer player) {
        if (player.connection == null || !player.connection.hasChannel(CutscenePayload.TYPE) || RUNS.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = player.getServer().overworld();
        Run run = new Run();
        CompoundTag restore = new CompoundTag();
        restore.putString("mode", player.gameMode.getGameModeForPlayer().getName());
        restore.putDouble("x", player.getX());
        restore.putDouble("y", player.getY());
        restore.putDouble("z", player.getZ());
        restore.putFloat("yaw", player.getYRot());
        player.getPersistentData().put(RESTORE, restore);
        run.deadline = level.getGameTime() + LOAD_TICKS;
        RUNS.put(player.getUUID(), run);
        // From the first moment: no walking off (the client holds the view) and nothing can hurt the player meanwhile.
        player.setGameMode(GameType.SPECTATOR);
        // The loading screen holds until the first shot.
        PacketDistributor.sendToPlayer(player, new CutscenePayload(player.blockPosition(), "expedition.story.prologue.loading", List.of(), true, CutscenePayload.Shot.AUTO));
        for (Shot shot : SHOTS) {
            if (shot.step() != null) {
                Route.reserve(player.getServer(), shot.step(), target -> run.lairs.put(shot.step(), target));
            }
        }
        Expedition.LOG.info("Prologue for {}: placing its lairs", player.getGameProfile().getName());
        return true;
    }

    /** The top of the Tree's trunk, where its spark sets out. */
    private static Vec3 crown(ServerLevel level) {
        return Vec3.atCenterOf(LandingCamp.tree(level).above(LandingCamp.HEIGHT));
    }

    /** A spark's flight from the crown to the grave, in ticks, and the pause at the grave before the next. */
    private static final int FLIGHT = 90;
    private static final int PAUSE = 30;

    /** In the Tree's shot: a spark flies down from the crown to the grave in an arc and flares there, over and over. */
    private static void spark(ServerPlayer player, Run run) {
        ServerLevel level = player.serverLevel();
        long t = (level.getGameTime() - run.shotStart) % (FLIGHT + PAUSE);
        Vec3 from = crown(level);
        Vec3 to = Vec3.atBottomCenterOf(LandingCamp.grave(level)).add(0, 0.3, 0);
        if (t < FLIGHT) {
            double k = t / (double) FLIGHT;
            // Out to the side and down, falling faster at the end.
            Vec3 at = from.lerp(to, k * k).add(Math.sin(k * Math.PI) * 4, Math.sin(k * Math.PI) * 2, 0);
            level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, true, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
            level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
        } else if (t == FLIGHT) {
            level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.SOUL, true, to.x, to.y, to.z, 25, 0.4, 0.2, 0.6, 0.03);
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, Run> entry : RUNS.entrySet()) {
            Run run = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && run.index >= 0 && run.index < run.shots.size() && run.shots.get(run.index).shot().step() == null) {
                spark(player, run);
            }
        }
        if (RUNS.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        for (Map.Entry<UUID, Run> entry : List.copyOf(RUNS.entrySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Run run = entry.getValue();
            if (player == null) {
                RUNS.remove(entry.getKey());
                continue;
            }
            boolean placed = SHOTS.stream().allMatch(shot -> shot.step() == null || run.lairs.containsKey(shot.step()));
            if (run.index < 0 && (placed || level.getGameTime() > run.deadline)) {
                stage(player, level, run);
                next(player, run);
            } else if (run.index >= 0 && level.getGameTime() > run.deadline) {
                next(player, run);
            }
        }
    }

    /** The shots, now that the lairs stand: each looks at its lair's middle; a missing or buried lair's words go on. */
    private static void stage(ServerPlayer player, ServerLevel level, Run run) {
        List<String> carried = new ArrayList<>();
        for (Shot shot : SHOTS) {
            for (int i = 0; i < shot.speakers().length(); i++) {
                carried.add("expedition.story.prologue." + shot.place() + "." + (i + 1));
            }
            Shots.Path path;
            if (shot.step() == null) {
                // From beside the crown down to the grave, looking at the grave.
                Vec3 grave = Vec3.atBottomCenterOf(LandingCamp.grave(level));
                Vec3 crown = crown(level);
                path = new Shots.Path(crown.add(-14, 4, 24), grave.add(4, 2.5, 8), grave.add(0, 0.5, 0)); // outside the branches' reach
                carried.addAll(actOneLines());
            } else {
                RouteData.Target lair = run.lairs.get(shot.step());
                BoundingBox box = lair == null ? null : lairBox(level, lair);
                path = box == null ? null : Shots.ofLair(level, box);
                if (path == null) {
                    Expedition.LOG.info("Prologue: no shot of {} ({}), its words go to the next", shot.step(), lair == null ? "not placed" : "no clear view");
                    continue;
                }
            }
            if (path == null) {
                continue; // the camp without a clear view: shouldn't happen; the player sees the lines over the next scene
            }
            ChunkPos chunk = new ChunkPos(BlockPos.containing(path.focus()));
            level.getChunkSource().addRegionTicket(TICKET, chunk, 3, chunk);
            run.shots.add(new Placed(shot, List.copyOf(carried), BlockPos.containing(path.focus()), path.from(), path.to()));
            carried.clear();
        }
        player.setGameMode(GameType.SPECTATOR);
    }

    /** For the command: places the lairs (if not yet) and tells each shot as it would be filmed. */
    public static void survey(ServerLevel level, java.util.function.Consumer<String> out) {
        for (Shot shot : SHOTS) {
            if (shot.step() == null) {
                continue;
            }
            Route.reserve(level.getServer(), shot.step(), lair -> {
                BoundingBox box = lair == null ? null : lairBox(level, lair);
                Shots.Path path = box == null ? null : Shots.ofLair(level, box);
                out.accept(shot.place() + " " + shot.step() + ": " + (lair == null ? "not placed" : path == null ? "no clear view at " + lair.pos.toShortString()
                    : "box " + box + ", focus " + BlockPos.containing(path.focus()).toShortString() + ", camera " + BlockPos.containing(path.from()).toShortString()
                    + " -> " + BlockPos.containing(path.to()).toShortString())
                    + insideText(level, lair));
            });
        }
    }

    private static String insideText(ServerLevel level, RouteData.Target lair) {
        Garrisons.Dungeon dungeon = lair == null ? null : lairDungeon(level, lair);
        Shots.Path inside = dungeon == null ? null : Shots.inside(level, dungeon.start());
        return inside == null ? "; inside: none" : "; inside: eye " + BlockPos.containing(inside.from()).toShortString() + " looking at "
            + BlockPos.containing(inside.focus()).toShortString();
    }

    /** A placed lair's frame (its chunk loaded for the lookup), or null. */
    static BoundingBox lairBox(ServerLevel level, RouteData.Target lair) {
        Garrisons.Dungeon dungeon = lairDungeon(level, lair);
        return dungeon == null ? null : dungeon.box();
    }

    /** A placed lair's dungeon (its chunk loaded for the lookup), or null. */
    static Garrisons.Dungeon lairDungeon(ServerLevel level, RouteData.Target lair) {
        level.getChunk(lair.pos);
        return Garrisons.dungeonsAt(level, lair.pos).stream().filter(dungeon -> dungeon.key().equals(lair.dungeonKey)).findFirst().orElse(null);
    }

    /** Act I's opening, spoken at the camp. */
    private static List<String> actOneLines() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Acts.get(1).intro().length(); i++) {
            lines.add("expedition.story.act.1." + (i + 1));
        }
        return lines;
    }

    /** Stages the next shot: the player (a spectator) above its place, the shot sent; or the end. */
    private static void next(ServerPlayer player, Run run) {
        run.index++;
        if (run.index >= run.shots.size()) {
            finish(player);
            return;
        }
        Placed placed = run.shots.get(run.index);
        ServerLevel level = player.getServer().overworld();
        // The shot's place itself: whatever the view distance, the client gets the chunks around it.
        player.teleportTo(level, placed.focus.getX() + 0.5, placed.focus.getY(), placed.focus.getZ() + 0.5, player.getYRot(), 30);
        List<CutscenePayload.Line> lines = placed.lines.stream().map(key -> new CutscenePayload.Line(Acts.speaker('T'), key)).toList();
        String title = run.index == 0 ? "expedition.story.prologue.title" : placed.shot.step() == null ? "expedition.story.act.1.title" : "";
        CutscenePayload.Shot shot = new CutscenePayload.Shot(vector(placed.from), vector(placed.to), run.index);
        PacketDistributor.sendToPlayer(player, new CutscenePayload(placed.focus, title, lines, true, shot));
        run.deadline = level.getGameTime() + SHOT_TICKS;
        run.shotStart = level.getGameTime();
    }

    private static Vector3f vector(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /** The client finished a shot, or skipped the prologue. */
    public static void onDone(ServerPlayer player, int sequence, boolean skipped) {
        Run run = RUNS.get(player.getUUID());
        if (run == null || sequence != run.index) {
            return;
        }
        if (skipped) {
            finish(player);
        } else {
            next(player, run);
        }
    }

    /** Back where the player stood, in their game mode. */
    private static void finish(ServerPlayer player) {
        RUNS.remove(player.getUUID());
        restore(player);
    }

    private static void restore(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(RESTORE);
        if (tag.isEmpty()) {
            return;
        }
        player.getPersistentData().remove(RESTORE);
        GameType mode = GameType.byName(tag.getString("mode"), GameType.SURVIVAL);
        player.teleportTo(player.getServer().overworld(), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), 0);
        player.setGameMode(mode);
        // Risen from the grave: earth and souls burst from it.
        ServerLevel level = player.serverLevel();
        if (player.blockPosition().closerThan(LandingCamp.grave(level), 3)) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, player.getX(), player.getY() + 0.3, player.getZ(), 30, 0.4, 0.3, 0.6, 0.04);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState()), player.getX(), player.getY(), player.getZ(), 40, 0.5, 0.2, 0.8, 0.1);
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1, 0.7f);
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1.5f, 0.8f);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && RUNS.containsKey(player.getUUID())) {
            finish(player);
        }
    }

    /**
     * The prologue's lairs are placed as the server starts, not when the first player joins: on a small server (3 cores,
     * busy pregenerating) placing six dungeons took longer than the loading screen waits. Placed once per world (Route).
     */
    @SubscribeEvent
    static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!(event.getServer().overworld().getChunkSource().getGenerator().getBiomeSource() instanceof net.expedition.world.realm.RealmBiomeSource)) {
            return; // not the realm world (the game-test server's flat one)
        }
        for (Shot shot : SHOTS) {
            if (shot.step() != null) {
                Route.reserve(event.getServer(), shot.step(), target -> Expedition.LOG.info("Prologue: {} {}", shot.step(),
                    target == null ? "could not be placed" : "stands at " + target.pos.toShortString()));
            }
        }
    }

    /** A prologue cut short by a server stop: put the player back on their next login. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !RUNS.containsKey(player.getUUID())) {
            restore(player);
        }
    }
}
