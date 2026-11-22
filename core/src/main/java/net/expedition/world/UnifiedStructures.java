package net.expedition.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.expedition.Expedition;
import net.expedition.route.RouteSteps;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * One placement system for every filler dungeon of the Overworld, the Nether and the End, whatever mod adds it.
 * The world is cut into {@link #CELL}x{@link #CELL}-chunk cells; {@link #FILL} of them get one dungeon, at a chunk away
 * from the cell's edges (so two dungeons are never closer than 2x{@link #MARGIN} chunks). The dungeon's type is chosen
 * among the structures that fit the biome there: every mod has an equal share, split among its structures. All of it
 * comes from the seed, so a world generates the same way every time. The mods' own placement of these structures is off.
 * Not managed: vanilla villages, Nether fortresses/bastions and End cities (vanilla progression), concentric sets
 * (strongholds), decor (trees, rocks, ore deposits), route dungeons (the atlas places them) and other dimensions.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class UnifiedStructures {
    private UnifiedStructures() {
    }

    public static final int CELL = 32;
    public static final int MARGIN = 7;
    /** Share of cells that get a dungeon at all. */
    static final double FILL = 0.65;
    static final int ATTEMPTS = 4;
    private static final Set<String> NATIVE_SETS = Set.of("minecraft:villages", "minecraft:nether_complexes", "minecraft:end_cities");
    private static final Set<String> DIMENSIONS = Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private static final Pattern DECOR = Pattern.compile(
        "tree|river|rock|boulder|bush|flower|log|lamp|statue|pond|spring|geode|crystal_cluster|fallen|stump|coral|deposit|ore_|_ore|vein|meteor|fossil");

    /** Structures this system places; the mods' own placement of them is cancelled. */
    private static volatile Set<Structure> managed = Set.of();
    /** Route dungeons: only the atlas places them (Structure.generate), never the world generation. */
    private static volatile Set<Structure> routeOnly = Set.of();
    private static final Map<Structure, String> MOD_OF = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Per dimension (generator state): candidates grouped by mod. */
    private static final Map<ChunkGeneratorStructureState, Map<String, List<Holder<Structure>>>> CANDIDATES =
        Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Boolean> PLACING = ThreadLocal.withInitial(() -> false);
    /** Set while one of the managed dimensions' generators makes a chunk's structures. */
    private static final ThreadLocal<Boolean> MANAGED_CHUNK = ThreadLocal.withInitial(() -> false);
    private static final Set<ChunkGenerator> OUR_GENERATORS = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    static boolean isDecor(ResourceLocation id) {
        return DECOR.matcher(id.getPath()).find();
    }

    @SubscribeEvent
    static void onServerStarting(ServerAboutToStartEvent event) {
        RegistryAccess registries = event.getServer().registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> route = new java.util.HashSet<>();
        RouteSteps.all().forEach(entry -> route.addAll(entry.step().structures()));
        Set<Structure> routeStructures = Collections.newSetFromMap(new IdentityHashMap<>());
        route.forEach(id -> structures.getOptional(id).ifPresent(routeStructures::add));
        routeOnly = routeStructures;
        // The generators of the three managed dimensions; other dimensions (Aether, Twilight Forest, moons) keep the mods' placement.
        Set<ChunkGenerator> generators = Collections.newSetFromMap(new IdentityHashMap<>());
        registries.registryOrThrow(Registries.LEVEL_STEM).entrySet().forEach(stem -> {
            if (DIMENSIONS.contains(stem.getKey().location().toString())) {
                generators.add(stem.getValue().generator());
            }
        });
        OUR_GENERATORS.clear();
        OUR_GENERATORS.addAll(generators);
        Set<Structure> result = Collections.newSetFromMap(new IdentityHashMap<>());
        registries.registryOrThrow(Registries.STRUCTURE_SET).entrySet().forEach(entry -> {
            String setId = entry.getKey().location().toString();
            StructureSet set = entry.getValue();
            if (set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement
                || NATIVE_SETS.contains(setId) || setId.endsWith(":villages")) {
                return;
            }
            for (StructureSet.StructureSelectionEntry selection : set.structures()) {
                ResourceLocation id = structures.getKey(selection.structure().value());
                if (id != null && !isDecor(id) && !route.contains(id)) {
                    result.add(selection.structure().value());
                    MOD_OF.put(selection.structure().value(), id.getNamespace());
                }
            }
        });
        managed = result;
        CANDIDATES.clear();
        Expedition.LOG.info("Unified structures: {} filler structures from {} mods, one per {}x{} chunks", result.size(),
            new java.util.HashSet<>(MOD_OF.values()).size(), CELL, CELL);
    }

    /** Also manage this generator (the realm world's, built when the levels are created). */
    public static void manage(ChunkGenerator generator) {
        OUR_GENERATORS.add(generator);
    }

    /** False for a managed structure the generator tries on its own (its native placement is off). */
    public static boolean allowNative(Structure structure) {
        if (routeOnly.contains(structure)) {
            return false;
        }
        return !MANAGED_CHUNK.get() || PLACING.get() || !managed.contains(structure);
    }

    /** Before a generator makes a chunk's structures: is it one of the managed dimensions? */
    public static void beginChunk(ChunkGenerator generator) {
        MANAGED_CHUNK.set(OUR_GENERATORS.contains(generator));
    }

    private static Map<String, List<Holder<Structure>>> candidates(ChunkGeneratorStructureState state, StructureManager structureManager) {
        return CANDIDATES.computeIfAbsent(state, s -> {
            Map<String, List<Holder<Structure>>> byMod = new LinkedHashMap<>();
            for (Holder<StructureSet> set : s.possibleStructureSets()) {
                for (StructureSet.StructureSelectionEntry selection : set.value().structures()) {
                    Structure structure = selection.structure().value();
                    if (managed.contains(structure)) {
                        List<Holder<Structure>> list = byMod.computeIfAbsent(MOD_OF.get(structure), k -> new ArrayList<>());
                        if (!list.contains(selection.structure())) {
                            list.add(selection.structure());
                        }
                    }
                }
            }
            return byMod;
        });
    }

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    /** The chunk of this cell where its dungeon goes. */
    public static ChunkPos cellChunk(long seed, int cellX, int cellZ) {
        long h = hash(seed, cellX, cellZ, 1);
        int span = CELL - 2 * MARGIN;
        return new ChunkPos(cellX * CELL + MARGIN + (int) Math.floorMod(h, span), cellZ * CELL + MARGIN + (int) Math.floorMod(h >>> 20, span));
    }

    /** Called after the generator made a chunk's structure starts: places the cell's dungeon if this is its chunk. */
    public static void placeCellDungeon(ChunkGenerator generator, RegistryAccess registries, ChunkGeneratorStructureState state,
                                        StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templates) {
        boolean ours = MANAGED_CHUNK.get();
        MANAGED_CHUNK.set(false);
        if (!ours || managed.isEmpty()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        long seed = state.getLevelSeed();
        int cellX = Math.floorDiv(pos.x, CELL);
        int cellZ = Math.floorDiv(pos.z, CELL);
        if (!cellChunk(seed, cellX, cellZ).equals(pos) || Math.floorMod(hash(seed, cellX, cellZ, 7), 1000) >= FILL * 1000) {
            return;
        }
        Map<String, List<Holder<Structure>>> byMod = candidates(state, structureManager);
        if (byMod.isEmpty()) {
            return;
        }
        var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getMiddleBlockX()), QuartPos.fromBlock(64),
            QuartPos.fromBlock(pos.getMiddleBlockZ()), state.randomState().sampler());
        Map<String, List<Holder<Structure>>> fitting = new LinkedHashMap<>();
        byMod.forEach((mod, list) -> {
            List<Holder<Structure>> ok = list.stream().filter(holder -> holder.value().biomes().contains(biome)).toList();
            if (!ok.isEmpty()) {
                fitting.put(mod, ok);
            }
        });
        List<String> mods = new ArrayList<>(fitting.keySet());
        if (mods.isEmpty()) {
            return;
        }
        SectionPos section = SectionPos.bottomOf(chunk);
        PLACING.set(true);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long h = hash(seed, cellX, cellZ, 100 + attempt);
                List<Holder<Structure>> list = fitting.get(mods.get((int) Math.floorMod(h, mods.size())));
                Holder<Structure> structure = list.get((int) Math.floorMod(h >>> 24, list.size()));
                if (((net.expedition.mixin.ChunkGeneratorInvoker) generator).expedition$tryGenerateStructure(
                    new StructureSet.StructureSelectionEntry(structure, 1), structureManager, registries, state.randomState(), templates, seed, chunk, pos, section)) {
                    return;
                }
            }
        } finally {
            PLACING.set(false);
        }
    }
}
