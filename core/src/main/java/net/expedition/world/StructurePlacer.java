package net.expedition.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Places any mod's structure at a position on demand and registers its start and references in the chunks, like
 * natural generation does, so structure lookups by position (garrisons, the route) see it. Vanilla
 * {@code /place structure} skips that registration and refuses unloaded areas.
 * {@code /locate} still won't find it: locate only probes the structure's natural placement grid.
 * Terrain is not adapted to the structure.
 */
public final class StructurePlacer {
    private StructurePlacer() {
    }

    /** Lays the structure out at {@code origin} without touching the world; null if it can't generate there. */
    public static StructureStart plan(ServerLevel level, Structure structure, BlockPos origin, boolean anyBiome) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
            level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(origin), 0, level,
            anyBiome ? biome -> true : structure.biomes()::contains);
        if (!start.isValid()) {
            return null;
        }
        String id = String.valueOf(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(structure));
        return SKY.contains(id) ? aloft(level, structure, start) : seat(level, structure, start, SEATED.contains(id));
    }

    /**
     * Surface buildings of a lifted realm that stand at their dimension's fixed heights (the Nether's lava sea, the
     * Twilight's own ground): in the realm's open land they would be buried. They are set on the ground around them
     * instead, a few blocks of their foundation in it.
     */
    static final java.util.Set<String> SEATED = java.util.Set.of("block_factorys_bosses:underworld_arena", "bosses_of_mass_destruction:gauntlet_arena",
        "cataclysm:burning_arena", "cataclysm:soul_black_smith", "incendium:sanctum", "twilightforest:naga_courtyard", "twilightforest:lich_tower",
        "twilightforest:dark_tower", "deeperdarker:ancient_temple");
    static final int FOUNDATION = 6;
    /** Flying structures (a sky whale and its ship): never inside a mountain, at least {@link #ALOFT} over the land. */
    static final java.util.Set<String> SKY = java.util.Set.of("sky_whale_ship:whale");
    static final int ALOFT = 40;

    private static StructureStart aloft(ServerLevel level, Structure structure, StructureStart start) {
        BoundingBox box = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()).orElse(null);
        int lift = box == null ? 0 : Math.min(groundAround(level, box) + ALOFT - box.minY(), level.getMaxBuildHeight() - 2 - box.maxY());
        return lift > 0 ? moved(structure, start, lift) : start;
    }

    private static StructureStart moved(Structure structure, StructureStart start, int lift) {
        start.getPieces().forEach(piece -> piece.move(0, lift, 0));
        // A new start over the moved pieces: the old one may have cached its box already (other mods read it in generate).
        return new StructureStart(structure, start.getChunkPos(), start.getReferences(),
            new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(start.getPieces()));
    }

    /** Rock left over an underground dungeon raised from deep down (the land's height varies a lot: some lie at fixed depths). */
    static final int UNDERGROUND_COVER = 20;
    static final int DEEP = 30;

    /**
     * A surface building of {@link #SEATED} set on the ground; an underground dungeon buried deeper than {@link #DEEP}
     * raised to {@link #UNDERGROUND_COVER} under it (still underground, but its outer layer can be dug through: Garrisons.isGuarded).
     */
    private static StructureStart seat(ServerLevel level, Structure structure, StructureStart start, boolean onGround) {
        BoundingBox box = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()).orElse(null);
        if (box == null) {
            return start;
        }
        int ground = groundAround(level, box);
        int lift = onGround ? ground - FOUNDATION - box.minY() : ground - box.maxY() > DEEP ? ground - UNDERGROUND_COVER - box.maxY() : 0;
        return lift > 0 ? moved(structure, start, lift) : start;
    }

    /** The land around a box from the generator's noise (no chunks), a sea or lava lake's surface counting as ground: median of points just outside its sides. */
    public static int groundAround(ServerLevel level, BoundingBox box) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            int x = box.minX() + box.getXSpan() * i / 4;
            int z = box.minZ() + box.getZSpan() * i / 4;
            for (int[] p : new int[][] {{x, box.minZ() - 6}, {x, box.maxZ() + 6}, {box.minX() - 6, z}, {box.maxX() + 6, z}}) {
                heights.add(generator.getBaseHeight(p[0], p[1], net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level,
                    level.getChunkSource().randomState()));
            }
        }
        heights.sort(Integer::compare);
        return heights.get(heights.size() / 2);
    }

    public static ChunkPos minChunk(StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        return new ChunkPos(box.minX() >> 4, box.minZ() >> 4);
    }

    public static ChunkPos maxChunk(StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        return new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);
    }

    /** Registers and builds a planned start in one go. Chunks that are not loaded yet are generated synchronously (slow). */
    public static void build(ServerLevel level, Structure structure, StructureStart start) {
        register(level, structure, start);
        ChunkPos.rangeClosed(minChunk(start), maxChunk(start)).forEach(pos -> buildChunk(level, start, pos));
    }

    /** Builds the part of a registered start that falls into one chunk (lets callers spread a big build over ticks). */
    public static void buildChunk(ServerLevel level, StructureStart start, ChunkPos pos) {
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
            new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ()), pos);
        // Built into an already loaded chunk, so the chunk-load pass never sees it: facades and ladders here.
        ChunkAccess chunk = level.getChunk(pos.x, pos.z);
        net.expedition.content.StructureFacades.process(chunk);
        net.expedition.content.StructureFacades.extendLadders(chunk);
    }

    /** Records the start and its references in the chunks so lookups by position find the structure. */
    public static void register(ServerLevel level, Structure structure, StructureStart start) {
        ChunkPos startChunk = start.getChunkPos();
        ChunkAccess originChunk = level.getChunk(startChunk.x, startChunk.z);
        originChunk.setStartForStructure(structure, start);
        originChunk.setUnsaved(true);
        long startKey = startChunk.toLong();
        ChunkPos.rangeClosed(minChunk(start), maxChunk(start)).forEach(pos -> {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z);
            chunk.addReferenceForStructure(structure, startKey);
            chunk.setUnsaved(true);
        });
    }

    /** Plan + build in one call (commands, tests). Null if the structure can't generate there. */
    public static StructureStart place(ServerLevel level, Structure structure, BlockPos origin) {
        StructureStart start = plan(level, structure, origin, true);
        if (start != null) {
            build(level, structure, start);
        }
        return start;
    }
}
