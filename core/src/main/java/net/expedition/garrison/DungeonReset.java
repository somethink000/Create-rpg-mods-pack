package net.expedition.garrison;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.expedition.Expedition;
import net.expedition.world.StructurePlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NG+: a cleared dungeon is rebuilt from its own structure (blocks, loot chests, spawners come back) and its garrison
 * starts over one level stronger. The rebuild loads the dungeon's chunks in the background and builds a few per tick.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class DungeonReset {
    private DungeonReset() {
    }

    static final int CHUNKS_PER_TICK = 4;
    static final int LOAD_TIMEOUT_TICKS = 20 * 60;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("expedition_reset", Comparator.comparingLong(ChunkPos::toLong));

    private record Job(ServerLevel level, StructureStart start, String key, List<ChunkPos> chunks, List<ChunkPos> toBuild, int[] waited) {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    /** The cleared dungeon the position is inside of, or null. */
    public static Garrisons.Dungeon clearedDungeonAt(ServerLevel level, BlockPos pos) {
        GarrisonData data = GarrisonData.get(level);
        for (Garrisons.Dungeon dungeon : Garrisons.dungeonsAt(level, pos)) {
            GarrisonRecord record = data.find(dungeon.key());
            if (record != null && record.state == GarrisonRecord.State.CLEARED && dungeon.box().isInside(pos)) {
                return dungeon;
            }
        }
        return null;
    }

    public static boolean busy(String key) {
        return JOBS.stream().anyMatch(job -> job.key().equals(key));
    }

    /** Starts rebuilding a cleared dungeon; its garrison returns one NG+ level stronger when done. */
    public static void start(ServerLevel level, Garrisons.Dungeon dungeon) {
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(StructurePlacer.minChunk(dungeon.start()), StructurePlacer.maxChunk(dungeon.start())).forEach(pos -> {
            level.getChunkSource().addRegionTicket(TICKET, pos, 1, pos);
            chunks.add(pos);
        });
        JOBS.add(new Job(level, dungeon.start(), dungeon.key(), chunks, new ArrayList<>(chunks), new int[1]));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (step(job)) {
                job.chunks().forEach(pos -> job.level().getChunkSource().removeRegionTicket(TICKET, pos, 1, pos));
                iterator.remove();
            }
        }
    }

    private static boolean step(Job job) {
        for (ChunkPos pos : job.chunks()) {
            if (job.level().getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                if (++job.waited()[0] > LOAD_TIMEOUT_TICKS) {
                    Expedition.LOG.warn("Dungeon reset {}: chunks did not load, giving up", job.key());
                    return true;
                }
                return false;
            }
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !job.toBuild().isEmpty(); i++) {
            StructurePlacer.buildChunk(job.level(), job.start(), job.toBuild().removeLast());
        }
        if (!job.toBuild().isEmpty()) {
            return false;
        }
        GarrisonData data = GarrisonData.get(job.level());
        GarrisonRecord record = data.getOrCreate(job.key());
        record.reset();
        data.setDirty();
        Expedition.LOG.info("Dungeon reset {}: NG+{}", job.key(), record.ng);
        var area = job.start().getBoundingBox().inflatedBy(64);
        for (ServerPlayer player : job.level().players()) {
            if (area.isInside(player.blockPosition())) {
                player.displayClientMessage(Component.translatable("expedition.reset.done", record.ng).withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        JOBS.forEach(job -> job.chunks().forEach(pos -> job.level().getChunkSource().removeRegionTicket(TICKET, pos, 1, pos)));
        JOBS.clear();
    }
}
