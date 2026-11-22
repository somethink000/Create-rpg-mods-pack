package net.expedition.content;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.expedition.Expedition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Dungeons are built from tens of thousands of wool, bookshelf and metal blocks — free resources for anyone with a
 * pickaxe. In freshly generated chunks those blocks are swapped for look-alike facades that break the same way but
 * drop nothing and can't be crafted. Natural terrain never contains these blocks (raw ore blocks do occur in ore veins,
 * so they are left alone); player blocks are untouched (only chunks no player has been near are processed).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class StructureFacades {
    private StructureFacades() {
    }

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Expedition.MOD_ID);

    /** Original block -> facade (registered with the original's properties, no loot). */
    private static final Map<Block, DeferredBlock<Block>> FACADES = new LinkedHashMap<>();
    private static volatile Map<Block, BlockState> replacements;

    private static void facade(String name, Block original) {
        FACADES.put(original, BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.ofFullCopy(original).noLootTable()));
    }

    static {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
            "blue", "brown", "green", "red", "black"};
        Block[] wool = {Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
            Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL,
            Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL};
        for (int i = 0; i < colors.length; i++) {
            facade(colors[i] + "_worn_canvas", wool[i]);
        }
        facade("old_bookshelf", Blocks.BOOKSHELF);
        facade("gold_facade", Blocks.GOLD_BLOCK);
        facade("iron_facade", Blocks.IRON_BLOCK);
        facade("diamond_facade", Blocks.DIAMOND_BLOCK);
        facade("emerald_facade", Blocks.EMERALD_BLOCK);
        facade("lapis_facade", Blocks.LAPIS_BLOCK);
        facade("redstone_facade", Blocks.REDSTONE_BLOCK);
        facade("netherite_facade", Blocks.NETHERITE_BLOCK);
    }

    /** The withered Tree's wood at spawn (LandingCamp): stripped bark that nothing breaks or moves, like bedrock. */
    public static final DeferredBlock<Block> WITHERED_WOOD = BLOCKS.registerSimpleBlock("withered_wood",
        BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD).strength(-1, 3600000).noLootTable()
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false));

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    static Map<Block, BlockState> replacements() {
        if (replacements == null) {
            Map<Block, BlockState> map = new HashMap<>();
            FACADES.forEach((original, facade) -> map.put(original, facade.get().defaultBlockState()));
            // Hay bales are free wheat: structures don't have them at all.
            map.put(Blocks.HAY_BLOCK, Blocks.AIR.defaultBlockState());
            replacements = map;
        }
        return replacements;
    }

    /**
     * Swaps the valuable blocks of a structure template's palettes for facades while it loads (hay: structure void, i.e.
     * nothing). Catches every way a mod builds from a template, including ships assembled as contraptions, which never
     * pass through chunk generation.
     */
    public static void processTemplate(net.minecraft.nbt.CompoundTag tag) {
        java.util.List<net.minecraft.nbt.ListTag> palettes = new java.util.ArrayList<>();
        if (tag.contains("palettes", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag all = tag.getList("palettes", net.minecraft.nbt.Tag.TAG_LIST);
            for (int i = 0; i < all.size(); i++) {
                palettes.add(all.getList(i));
            }
        } else if (tag.contains("palette", net.minecraft.nbt.Tag.TAG_LIST)) {
            palettes.add(tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
        Map<String, String> names = templateNames();
        for (net.minecraft.nbt.ListTag palette : palettes) {
            for (int i = 0; i < palette.size(); i++) {
                net.minecraft.nbt.CompoundTag entry = palette.getCompound(i);
                String replacement = names.get(entry.getString("Name"));
                if (replacement != null) {
                    entry.putString("Name", replacement);
                    entry.remove("Properties");
                }
            }
        }
    }

    private static volatile Map<String, String> templateNames;

    private static Map<String, String> templateNames() {
        if (templateNames == null) {
            Map<String, String> names = new HashMap<>();
            replacements().forEach((original, state) -> names.put(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(original).toString(),
                state.isAir() ? "minecraft:structure_void" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
            templateNames = names;
        }
        return templateNames;
    }

    /** Swaps valuable structure blocks in a chunk for facades; returns how many were swapped. */
    public static int process(ChunkAccess chunk) {
        Map<Block, BlockState> map = replacements();
        int swapped = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(state -> map.containsKey(state.getBlock()))) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState facade = map.get(section.getBlockState(x, y, z).getBlock());
                        if (facade != null) {
                            section.setBlockState(x, y, z, facade, false);
                            swapped++;
                        }
                    }
                }
            }
        }
        if (swapped > 0) {
            chunk.setUnsaved(true);
        }
        return swapped;
    }

    /**
     * Containers whose items are baked into a structure template (Create buildings, sky islands) bypass loot tables:
     * their contents are replaced by generated dungeon loot of the place's tier.
     */
    static int refillPrefilled(net.minecraft.server.level.ServerLevel level, ChunkAccess chunk) {
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk full) || chunk.getAllReferences().isEmpty() && chunk.getAllStarts().isEmpty()) {
            return 0;
        }
        int refilled = 0;
        for (var blockEntity : full.getBlockEntities().values()) {
            if (!(blockEntity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container)
                || container.getLootTable() != null || container.isEmpty()) {
                continue;
            }
            int stacks = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    stacks++;
                }
            }
            container.clearContent();
            var loot = ChestLoot.generate(level, net.minecraft.world.phys.Vec3.atCenterOf(container.getBlockPos()), level.random,
                Math.max(ChestLoot.MIN_ROLLS, Math.min(ChestLoot.MAX_ROLLS, stacks)));
            java.util.List<Integer> slots = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, container.getContainerSize()).boxed().toList());
            net.minecraft.Util.shuffle(slots, level.random);
            for (int i = 0; i < loot.size() && i < slots.size(); i++) {
                container.setItem(slots.get(i), loot.get(i));
            }
            container.setChanged();
            refilled++;
        }
        return refilled;
    }

    /** Air this far under a structure's ladder may still end at water (a ship's side ladder above the sea). */
    static final int LADDER_GAP = 5;
    static final int LADDER_UNDERWATER = 2;

    /**
     * Ladders of ships and docks often end a few blocks above the water, out of reach from a boat. A ladder hanging over
     * water is extended down to it and {@value #LADDER_UNDERWATER} blocks in (waterlogged), with a plank behind where the
     * hull curves away. Returns how many ladder blocks were added.
     */
    public static int extendLadders(ChunkAccess chunk) {
        if (chunk.getAllReferences().isEmpty() && chunk.getAllStarts().isEmpty()) {
            return 0;
        }
        int added = 0;
        int minY = chunk.getMinBuildHeight();
        net.minecraft.world.level.ChunkPos cp = chunk.getPos();
        for (int i = 0; i < chunk.getSections().length; i++) {
            LevelChunkSection section = chunk.getSections()[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(net.minecraft.world.level.block.Blocks.LADDER))) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState ladder = section.getBlockState(x, y, z);
                        if (ladder.is(net.minecraft.world.level.block.Blocks.LADDER)) {
                            added += extendDown(chunk, new net.minecraft.core.BlockPos(cp.getMinBlockX() + x, minY + i * 16 + y, cp.getMinBlockZ() + z), ladder);
                        }
                    }
                }
            }
        }
        if (added > 0) {
            chunk.setUnsaved(true);
            Expedition.LOG.debug("Ladders: {} blocks added down to the water in chunk {}", added, cp);
        }
        return added;
    }

    public static int extendDown(ChunkAccess chunk, net.minecraft.core.BlockPos bottom, BlockState ladder) {
        if (ladder.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            return 0; // already reaches into the water (or was extended before): keeps the pass idempotent
        }
        var below = bottom.below();
        if (!chunk.getBlockState(below).isAir() && !chunk.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) {
            return 0; // ends on something (deck, floor) or is not the bottom of the ladder
        }
        int gap = 0;
        while (gap < LADDER_GAP && chunk.getBlockState(below.below(gap)).isAir()) {
            gap++;
        }
        var water = below.below(gap);
        if (!chunk.getBlockState(water).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return 0; // a tower or cliff ladder, not one above the sea
        }
        var facing = ladder.getValue(net.minecraft.world.level.block.LadderBlock.FACING);
        BlockState wall = chunk.getBlockState(bottom.relative(facing.getOpposite()));
        int added = 0;
        for (int dy = 1; dy <= gap + LADDER_UNDERWATER; dy++) {
            var pos = bottom.below(dy);
            boolean wet = dy > gap;
            if (wet && !chunk.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WATER)) {
                break;
            }
            var behind = pos.relative(facing.getOpposite());
            if (new net.minecraft.world.level.ChunkPos(behind).equals(chunk.getPos()) && !chunk.getBlockState(behind).isFaceSturdy(chunk, behind, facing)) {
                if (!wall.isSolidRender(chunk, behind) || chunk.getBlockState(behind).hasBlockEntity()) {
                    break;
                }
                chunk.setBlockState(behind, wall, false);
            }
            chunk.setBlockState(pos, ladder.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, wet), false);
            added++;
        }
        return added;
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        // New chunks, plus chunks no player has ever been near (saved half-generated at the edge of loaded land and
        // finished later); a chunk where a player built anything has inhabited time, so player blocks are never touched.
        if (!event.getLevel().isClientSide() && (event.isNewChunk() || event.getChunk().getInhabitedTime() == 0)) {
            process(event.getChunk());
            extendLadders(event.getChunk());
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                refillPrefilled(level, event.getChunk());
            }
        }
    }
}
