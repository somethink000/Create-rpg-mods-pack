package net.expedition.garrison;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.expedition.Expedition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Which blocks of a chunk a dungeon placed: written while a hostile structure builds itself (StructureStartMixin and
 * ChunkBlockMixin), forgotten where world generation puts something else over them later (a tree, an ore). Only these
 * are locked in an uncleared dungeon: rock, hills and trees its frame swallowed stay the world's.
 *
 * <p>A chunk generated before this existed has no record: there the whole dungeon frame is locked as it used to be.
 */
public final class DungeonBlocks {
    /** Bits of one 16x16x16 section, by section index (y >> 4). */
    private final Int2ObjectOpenHashMap<long[]> sections = new Int2ObjectOpenHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Expedition.MOD_ID);
    public static final java.util.function.Supplier<AttachmentType<DungeonBlocks>> TYPE = ATTACHMENTS.register("dungeon_blocks",
        () -> AttachmentType.builder(DungeonBlocks::new).serialize(new IAttachmentSerializer<CompoundTag, DungeonBlocks>() {
            @Override
            public DungeonBlocks read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                DungeonBlocks blocks = new DungeonBlocks();
                for (String key : tag.getAllKeys()) {
                    long[] bits = tag.getLongArray(key);
                    if (bits.length == 64) {
                        blocks.sections.put(Integer.parseInt(key), bits);
                    }
                }
                return blocks;
            }

            @Override
            public CompoundTag write(DungeonBlocks blocks, HolderLookup.Provider provider) {
                CompoundTag tag = new CompoundTag(); // kept even empty: the chunk is known to be recorded
                blocks.sections.int2ObjectEntrySet().forEach(e -> tag.putLongArray(Integer.toString(e.getIntKey()), e.getValue()));
                return tag;
            }
        }).build());

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private static int bit(BlockPos pos) {
        return (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
    }

    void set(BlockPos pos, boolean placed) {
        long[] bits = sections.get(pos.getY() >> 4);
        if (bits == null) {
            if (!placed) {
                return;
            }
            bits = new long[64];
            sections.put(pos.getY() >> 4, bits);
        }
        int bit = bit(pos);
        if (placed) {
            bits[bit >> 6] |= 1L << bit;
        } else {
            bits[bit >> 6] &= ~(1L << bit);
        }
    }

    boolean has(BlockPos pos) {
        long[] bits = sections.get(pos.getY() >> 4);
        int bit = bit(pos);
        return bits != null && (bits[bit >> 6] & 1L << bit) != 0;
    }

    // ---------------------------------------------------------------- hooks (mixins)

    /** Depth of hostile structure building on this thread: blocks set meanwhile are the dungeon's. */
    private static final ThreadLocal<int[]> BUILDING = ThreadLocal.withInitial(() -> new int[1]);

    public static void beginBuild(ChunkAccess chunk) {
        chunk.getData(TYPE); // the chunk is recorded from now on, blocks or not
        BUILDING.get()[0]++;
    }

    public static void endBuild() {
        BUILDING.get()[0]--;
    }

    /** Every block set in a chunk: built by a dungeon, or (world generation only) put over one of its blocks. */
    public static void onSet(ChunkAccess chunk, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (BUILDING.get()[0] > 0) {
            chunk.getData(TYPE).set(pos, !state.isAir());
        } else if (!(chunk instanceof LevelChunk)) {
            // A live chunk's changes (a door opened, a furnace lit) keep the block the dungeon's.
            DungeonBlocks blocks = chunk.getExistingDataOrNull(TYPE);
            if (blocks != null) {
                blocks.set(pos, false);
            }
        }
    }

    /** Placed by a dungeon, or the chunk predates the record (then every block of a dungeon frame counts as its). */
    public static boolean placed(ServerLevel level, BlockPos pos) {
        DungeonBlocks blocks = level.getChunkAt(pos).getExistingDataOrNull(TYPE);
        return blocks == null || blocks.has(pos);
    }

    /**
     * The world's own solid block (rock, earth, ore) at {@code pos}: the chunk is recorded and the dungeon didn't place it.
     * Air, liquids and blocks without collision (a torch on the wall) aren't: a wall broken through leaves air, so the
     * layer behind it stays locked.
     */
    static boolean foreign(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        DungeonBlocks blocks = level.getChunkAt(pos).getExistingDataOrNull(TYPE);
        var state = level.getBlockState(pos);
        return blocks != null && !blocks.has(pos) && opens(level, pos, state);
    }

    /** A block that would lay a dungeon block next to it open (see foreign). */
    public static boolean opens(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return !state.getCollisionShape(level, pos).isEmpty() && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock);
    }

    /** A dungeon block on its outer face: it touches the world's rock, so it can be dug through from there. */
    public static boolean onShell(ServerLevel level, BlockPos pos) {
        return onShell(level, pos, null);
    }

    /** As {@link #onShell(ServerLevel, BlockPos)}, not counting the block at {@code except} (one just placed). */
    public static boolean onShell(ServerLevel level, BlockPos pos, BlockPos except) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!next.equals(except) && foreign(level, next)) {
                return true;
            }
        }
        return false;
    }

    /** Placed by a dungeon, known for sure (a chunk without a record says no). */
    public static boolean recorded(ServerLevel level, BlockPos pos) {
        DungeonBlocks blocks = level.getChunkAt(pos).getExistingDataOrNull(TYPE);
        return blocks != null && blocks.has(pos);
    }
}
