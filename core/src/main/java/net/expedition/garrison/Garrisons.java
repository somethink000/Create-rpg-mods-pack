package net.expedition.garrison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.expedition.Expedition;
import net.expedition.route.Route;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dungeon garrisons. Spawners are a farm, not a fight: they are removed, and every dungeon gets a fixed, saved number
 * of kills instead (the bar). Mobs come around players exploring it, a few at a time, preferably out of sight, as many
 * at the end as at the start, until the bar is empty. Then the dungeon is cleared and the
 * garrison's leftovers vanish. Until then the blocks it placed itself (DungeonBlocks) can't be broken or blown up and it can't be
 * built over (instant-break blocks like torches excepted); rock and trees inside its frame are the world's and can be dug.
 *
 * <p>Mob types and proportions (see {@link #mobWeights}): the core is the dungeon's spawners (3 skeleton + 1 zombie
 * spawner = 75% skeletons) or the structure's own monster spawns, joined by kin (monsters of the structure's own mod)
 * and locals (modded monsters of the biome), so dungeons of different mods and realms field different mobs; the plain
 * zombie-skeleton-spider core gives up half its share when there is anything else. Only structures with
 * spawners, loot chests or own monster spawns get a garrison. Spawners outside structures become a small pack.
 * Also: hostile ground mobs open wooden doors, so a door no longer protects from anything.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class Garrisons {
    private Garrisons() {
    }

    public static final String TAG = Expedition.MOD_ID + ":garrison";
    private static final String MONSTER_ROOM = "monster_room";

    /**
     * Garrison size grows with the square root of the dungeon's floor area (see floorArea). Measured: small dungeon 24
     * floor -> 4 mobs, jungle temple 48 -> 4, skeleton crypt 528 -> 9, outpost 600 -> 10, dragon tower 1728 -> 17,
     * trial chambers 2568 -> 20, zombie catacomb 2838 -> 21, ancient city 6519 -> 32, spider cave 7508 -> 35 (before loot).
     */
    static final double BUDGET_PER_SQRT_AREA = 0.4;
    static final int BUDGET_PER_SPAWNER = 1;
    static final int BUDGET_SPAWNERS_COUNTED = 8;
    static final int BUDGET_PER_CHEST = 1;
    /** Loot only adds a little: some structures are full of decorative barrels (a sky village counted 75). */
    public static final int BUDGET_CHESTS_COUNTED = 24;
    static final int BUDGET_MIN = 4;
    public static final int BUDGET_MAX = 160;
    /** Big complexes grow faster than sqrt(area): floor 6500 -> x1.3, 25000 -> x1.6 (a five-tower fort holds ~100). */
    static final double BUDGET_LARGE_SCALE = 250;
    /** Structures smaller than this (haystacks, wells, single trees) are decoration, not dungeons. */
    static final int MIN_DUNGEON_VOLUME = 1500;
    /** Weak or out-of-place monsters never fill a garrison taken from the biome's spawn list. */
    private static final List<String> NOT_GARRISON = List.of("minecraft:slime", "minecraft:magma_cube", "minecraft:silverfish",
        "minecraft:endermite", "minecraft:phantom", "minecraft:witch", "minecraft:bat", "minecraft:enderman");
    private static final Map<String, Integer> FALLBACK_MOBS = Map.of("minecraft:zombie", 4, "minecraft:skeleton", 4, "minecraft:spider", 2);
    /** Per NG+ level: +25% mobs, +50% health, +25% damage. */
    static final double NG_BUDGET = 0.25;
    static final double NG_HEALTH_BONUS = 0.5;
    static final double NG_DAMAGE_BONUS = 0.25;
    private static final ResourceLocation NG_HEALTH = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "ng_health");
    private static final ResourceLocation NG_DAMAGE = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "ng_damage");

    static final int SCAN_INTERVAL = 20;
    static final int SPAWN_INTERVAL = 60;
    static final int SPAWN_PER_WAVE = 2;
    /** Garrison mobs alive at once in the whole dungeon. */
    static final int MAX_ALIVE_IN_DUNGEON = 8;
    /** A small garrison never has more than a quarter of itself (plus two) out at once. */
    static int maxAlive(GarrisonRecord record) {
        return Math.min(MAX_ALIVE_IN_DUNGEON, 2 + record.total / 4);
    }
    /** A player's share of the garrison: mobs this near them; players this near share it (at most this many count). */
    public static final int PLAYER_SHARE_RADIUS = 24;
    static final int MAX_PLAYERS_COUNTED = 4;

    static final int SPAWN_MIN_DISTANCE = 8;
    static final int SPAWN_MAX_DISTANCE = 20;
    /** Outdoor dungeons have no hidden spots: then mobs may appear in sight, but this far away. */
    static final int VISIBLE_SPAWN_MIN_DISTANCE = 14;
    static final int SPAWN_MAX_HEIGHT_DIFF = 6;
    static final int SMALL_DUNGEON_MIN_DISTANCE = 4;
    static final int MONSTER_ROOM_PACK = 3;
    static final int SCAN_CHUNK_RADIUS = 3;
    /** The garrison wakes up this far outside the dungeon's frame (the whole frame counts, gaps between parts too). */
    static final int ACTIVATION_MARGIN = 16;

    /** The dungeon's name for the garrison bar: the route step's title, else the structure's (translated, or from its id). */
    public static Component dungeonName(net.minecraft.server.MinecraftServer server, String key) {
        Component route = net.expedition.route.Route.titleFor(server, key);
        if (route != null) {
            return route;
        }
        ResourceLocation id = ResourceLocation.parse(key.substring(0, key.indexOf('|')));
        String path = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace('_', ' ');
        return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
            Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    /** Only Minecraft's own villages (and buried treasure) are peaceful; decor (trees, rocks, rivers) is no dungeon. */
    private static final java.util.regex.Pattern DECOR = java.util.regex.Pattern.compile(
        "tree|river|rock|boulder|bush|flower|log|lamp|statue|pond|spring|geode|crystal_cluster|fallen|stump|mushroom_?(big|giant)|coral|fossil");

    /** A dungeon structure start covering a position. */
    public record Dungeon(StructureStart start, Structure structure, String id, String key) {
        public BoundingBox box() {
            return start.getBoundingBox();
        }
    }

    // ---------------------------------------------------------------- structure lookup

    public static String structureId(ServerLevel level, Structure structure) {
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        return String.valueOf(id);
    }

    static boolean isPeaceful(String id, Structure structure) {
        return isPeacefulId(id);
    }

    public static boolean isPeacefulId(String id) {
        return id.startsWith("minecraft:village") || id.equals("minecraft:buried_treasure") || DECOR.matcher(id.substring(id.indexOf(':') + 1)).find();
    }

    /** Dungeon structure starts whose area covers the chunk at {@code pos}. */
    public static List<Dungeon> dungeonsAt(ServerLevel level, BlockPos pos) {
        List<Dungeon> result = new ArrayList<>();
        var manager = level.structureManager();
        manager.getAllStructuresAt(pos).forEach((structure, references) -> {
            String id = structureId(level, structure);
            if (isPeaceful(id, structure)) {
                return;
            }
            manager.fillStartsForStructure(structure, references, start -> {
                if (start.isValid()) {
                    ChunkPos chunk = start.getChunkPos();
                    result.add(new Dungeon(start, structure, id, id + "|" + chunk.x + "," + chunk.z));
                }
            });
        });
        return result;
    }

    /** Dungeons whose frame, grown by {@code margin}, contains {@code pos}. */
    public static List<Dungeon> dungeonsNear(ServerLevel level, BlockPos pos, int margin) {
        Map<String, Dungeon> found = new LinkedHashMap<>();
        int chunks = (margin >> 4) + 1;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                for (Dungeon dungeon : dungeonsAt(level, pos.offset(dx * 16, 0, dz * 16))) {
                    if (dungeon.box().inflatedBy(margin).isInside(pos)) {
                        found.putIfAbsent(dungeon.key(), dungeon);
                    }
                }
            }
        }
        return List.copyOf(found.values());
    }

    /** Inside the frame of a hostile structure (garrisoned or cleared): its chests pay for a fight. Villages are not. */
    public static boolean inDungeon(ServerLevel level, BlockPos pos) {
        return dungeonsAt(level, pos).stream().anyMatch(dungeon -> dungeon.box().isInside(pos));
    }

    public static boolean inPiece(ServerLevel level, Dungeon dungeon, BlockPos pos) {
        return level.structureManager().structureHasPieceAt(pos, dungeon.start());
    }

    static Map<String, Integer> structureMonsters(Structure structure) {
        StructureSpawnOverride override = structure.spawnOverrides().get(MobCategory.MONSTER);
        return override == null ? Map.of() : weights(override.spawns().unwrap());
    }

    private static Map<String, Integer> weights(List<MobSpawnSettings.SpawnerData> spawns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (MobSpawnSettings.SpawnerData data : spawns) {
            result.merge(EntityType.getKey(data.type).toString(), data.getWeight().asInt(), Integer::sum);
        }
        return result;
    }

    // ---------------------------------------------------------------- protection

    /** Every hostile structure big enough to be a dungeon has a garrison, loot or not. */
    static boolean isEligible(GarrisonRecord record, Dungeon dungeon) {
        BoundingBox box = dungeon.box();
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        return record.spawners > 0 || record.chests > 0 || !structureMonsters(dungeon.structure()).isEmpty() || volume >= MIN_DUNGEON_VOLUME;
    }

    static boolean isProtected(ServerLevel level, Dungeon dungeon) {
        GarrisonData data = GarrisonData.get(level);
        GarrisonRecord record = data.find(dungeon.key());
        if (record == null) {
            // Never seen by a player yet (e.g. shelled from afar): scan its loaded chunks now so the decision is real.
            BoundingBox box = dungeon.box();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk != null) {
                        scanChunk(level, chunk);
                    }
                }
            }
            record = data.getOrCreate(dungeon.key());
        }
        if (record.state == GarrisonRecord.State.CLEARED) {
            return false;
        }
        return record.state == GarrisonRecord.State.ACTIVE || isEligible(record, dungeon);
    }

    /** True if {@code pos} is inside a piece of a dungeon that is not cleared yet. */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return isProtected(level, pos, dungeonsAt(level, pos));
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos, List<Dungeon> dungeons) {
        return dungeons.stream().anyMatch(dungeon -> inPiece(level, dungeon, pos) && isProtected(level, dungeon));
    }

    /**
     * A block the dungeon placed itself (DungeonBlocks), in a dungeon not cleared yet: it can't be broken or blown up.
     * Rock, hills and trees inside its frame are the world's and can be dug, and so is the dungeon's outer layer where
     * it touches them: a buried dungeon is dug into anywhere, one layer deep (the layer behind faces the hole's air).
     */
    public static boolean isGuarded(ServerLevel level, BlockPos pos) {
        return guardedBlock(level, pos) && isProtected(level, pos);
    }

    private static boolean guardedBlock(ServerLevel level, BlockPos pos) {
        return DungeonBlocks.placed(level, pos) && !DungeonBlocks.onShell(level, pos);
    }

    // ---------------------------------------------------------------- scanning

    /** The mob a spawner makes: a plain spawner's spawn data, a trial spawner's first spawn potential. */
    private static String spawnerType(ServerLevel level, BlockEntity spawner) {
        return entityId(spawner.saveWithoutMetadata(level.registryAccess()));
    }

    private static String entityId(CompoundTag tag) {
        if (tag.contains("entity", net.minecraft.nbt.Tag.TAG_COMPOUND) && !tag.getCompound("entity").getString("id").isEmpty()) {
            return tag.getCompound("entity").getString("id");
        }
        for (String key : tag.getAllKeys()) {
            net.minecraft.nbt.Tag child = tag.get(key);
            String id = child instanceof CompoundTag compound ? entityId(compound)
                : child instanceof net.minecraft.nbt.ListTag list && !list.isEmpty() && list.get(0) instanceof CompoundTag first ? entityId(first) : null;
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** Plain and trial spawners (arenas, trial chambers): both are farms, both make way for the garrison. */
    private static boolean isSpawner(BlockEntity blockEntity) {
        return blockEntity instanceof SpawnerBlockEntity || blockEntity instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
    }

    /** The (empty) loot table a pre-filled chest gets: opened, it is filled like any dungeon chest (ChestLoot). */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> REFILL =
        net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "chests/dungeon"));

    /**
     * A chest whose items were placed in the structure's template (Create buildings: pipes, shafts, finished parts) has
     * no loot table, so the loot rules never saw it: seen for the first time in an uncleared dungeon, it is emptied and
     * given a loot table, and holds a dungeon chest's materials when opened. (No player chest stands there: nothing can
     * be placed in an uncleared dungeon.)
     */
    private static void refill(ServerLevel level, BlockPos pos, GarrisonRecord record) {
        if (record.state != GarrisonRecord.State.CLEARED && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
            && container.getLootTable() == null && !container.isEmpty()) {
            container.clearContent();
            container.setLootTable(REFILL, level.random.nextLong());
            container.setChanged();
        }
    }

    /** Removes spawners and counts loot chests in one loaded chunk. */
    static void scanChunk(ServerLevel level, LevelChunk chunk) {
        GarrisonData data = GarrisonData.get(level);
        long chunkKey = chunk.getPos().toLong();
        List<BlockEntity> spawners = new ArrayList<>();
        List<BlockPos> chests = new ArrayList<>();
        // For 49 chunks around each player (each at most every RESCAN_TICKS): chests (a player's storage too) only matter where a structure is.
        boolean structures = !chunk.getAllReferences().isEmpty() || !chunk.getAllStarts().isEmpty();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (isSpawner(blockEntity)) {
                spawners.add(blockEntity);
            } else if (structures && blockEntity instanceof RandomizableContainerBlockEntity container
                && (container.getLootTable() != null || !container.isEmpty())) {
                // Loot comes from a loot table or is placed in the structure template itself (Create buildings).
                chests.add(blockEntity.getBlockPos());
            }
        }
        List<GarrisonRecord> counted = new ArrayList<>();
        for (BlockPos pos : chests) {
            Dungeon owner = dungeonsAt(level, pos).stream().filter(d -> inPiece(level, d, pos)).findFirst().orElse(null);
            if (owner == null) {
                continue;
            }
            GarrisonRecord record = data.getOrCreate(owner.key());
            if (record.scannedChunks.contains(chunkKey)) {
                continue;
            }
            record.chests++;
            counted.add(record);
            refill(level, pos, record);
        }
        counted.forEach(record -> record.scannedChunks.add(chunkKey));
        if (spawners.isEmpty()) {
            if (!counted.isEmpty()) {
                data.setDirty();
            }
            return;
        }

        for (BlockEntity spawner : spawners) {
            BlockPos pos = spawner.getBlockPos();
            String type = spawnerType(level, spawner);
            level.removeBlock(pos, false);
            Dungeon owner = dungeonsAt(level, pos).stream().filter(d -> d.box().isInside(pos)).findFirst().orElse(null);
            if (owner == null) {
                if (type != null) {
                    for (int i = 0; i < MONSTER_ROOM_PACK; i++) {
                        BlockPos spot = randomFloorNear(level, pos, 4);
                        if (spot != null) {
                            spawnMob(level, type, spot, MONSTER_ROOM, 0);
                        }
                    }
                }
                continue;
            }
            GarrisonRecord record = data.getOrCreate(owner.key());
            record.spawners++;
            if (type != null) {
                record.spawnerTypes.merge(type, 1, Integer::sum);
            }
            // The garrison's size is fixed when it starts (shown on its bar); a spawner found later only adds its mob type and spot.
        }
        data.setDirty();
    }

    /**
     * Special (uncraftable mod) chest items belong to the dungeon of the same mod (a Cataclysm altar item in a Cataclysm
     * structure): its garrison carries them, and once the dungeon is cleared they stay in the chest as the reward.
     * Returns what may stay in the chest; everything else (a dragon bone some mod injected into village chests) is dropped.
     */
    public static List<ItemStack> handleSpecialLoot(ServerLevel level, BlockPos pos, List<ItemStack> loot) {
        for (Dungeon dungeon : dungeonsAt(level, pos)) {
            if (!dungeon.box().isInside(pos)) {
                continue;
            }
            String mod = dungeon.id().substring(0, dungeon.id().indexOf(':'));
            List<ItemStack> own = loot.stream()
                .filter(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(mod)).toList();
            if (own.isEmpty()) {
                return List.of();
            }
            GarrisonData data = GarrisonData.get(level);
            GarrisonRecord record = data.getOrCreate(dungeon.key());
            if (record.state == GarrisonRecord.State.CLEARED) {
                return own;
            }
            if (!isEligible(record, dungeon)) {
                return List.of();
            }
            own.forEach(stack -> record.specialDrops.add(stack.copy()));
            data.setDirty();
            return List.of();
        }
        return List.of();
    }

    /** Scans every loaded chunk of a dungeon (commands, protection of unseen dungeons). */
    public static void scanDungeon(ServerLevel level, Dungeon dungeon) {
        BoundingBox box = dungeon.box();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    scanChunk(level, chunk);
                }
            }
        }
    }

    public static boolean eligible(ServerLevel level, Dungeon dungeon) {
        GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
        return record != null && isEligible(record, dungeon);
    }

    /** A chunk's spawners and chests hardly change: each is looked through at most this often (ticks). */
    static final int RESCAN_TICKS = 600;
    private static final java.util.Map<ServerLevel, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap> SCANNED = new java.util.WeakHashMap<>();

    static void scanAround(ServerLevel level, ServerPlayer player) {
        ChunkPos center = player.chunkPosition();
        long now = level.getGameTime();
        var scanned = SCANNED.computeIfAbsent(level, k -> new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap());
        if (scanned.size() > 50_000) {
            scanned.clear();
        }
        for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
            for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
                long key = ChunkPos.asLong(center.x + dx, center.z + dz);
                if (now - scanned.getOrDefault(key, Long.MIN_VALUE / 2) < RESCAN_TICKS) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk != null) {
                    scanChunk(level, chunk);
                    scanned.put(key, now);
                }
            }
        }
    }

    // ---------------------------------------------------------------- spawning

    /** Plain monsters every world has: a garrison of only these is the dull one. */
    private static final Set<String> PLAIN = Set.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper",
        "minecraft:husk", "minecraft:stray", "minecraft:cave_spider", "minecraft:zombie_villager");
    /** Kin and locals joining a garrison at most, and the weight each gets (the core's total is scaled to CORE_SHARE). */
    static final int KIN = 2, LOCALS = 2, JOINER_WEIGHT = 3, CORE_SHARE = 10;
    /** Heavier than this is a miniboss, not a garrison soldier. */
    static final double MAX_SOLDIER_HEALTH = 80;
    private static volatile Map<String, List<String>> soldiers;
    /**
     * Not soldiers though hostile: allies and summons (a boss's minions, a player's followers), babies, projectiles
     * registered as mobs, water creatures without a water spawn rule, toys, the grottol (a fleeing diamond piñata), cutscene
     * and body-part entities (the kraken's), a player's risen allies (Twilight's loyal zombie).
     */
    private static final java.util.regex.Pattern NOT_SOLDIER = java.util.regex.Pattern.compile(
        "_player$|follower|minion|:baby_|fireball|pile_of_bones|_pot$|^fdbosses:|^create_confectionery:|^create_things_and_misc:"
        + "|^aquamirae:(maw|eel)$|lionfish|^minecraft:(giant|illusioner)$|grottol|cinematic|tentacle|:loyal_");

    /** Monsters fit to garrison a dungeon, by mod: ground or flying hostiles, no bosses or minibosses, no water mobs. */
    public static Map<String, List<String>> soldiers() {
        if (soldiers == null) {
            var bossTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "bosses"));
            Map<String, List<String>> byMod = new java.util.TreeMap<>();
            for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                String id = EntityType.getKey(type).toString();
                if (type.getCategory() != MobCategory.MONSTER || !type.canSummon() || type.is(bossTag) || net.expedition.route.StoryBosses.isBoss(type)
                    || NOT_GARRISON.contains(id) || NOT_SOLDIER.matcher(id).find() || net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)
                    == net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER || !soldierHealth(type)) {
                    continue;
                }
                byMod.computeIfAbsent(id.substring(0, id.indexOf(':')), k -> new ArrayList<>()).add(id);
            }
            soldiers = byMod;
            Expedition.LOG.info("Garrison soldiers: {} monster types from {} mods", byMod.values().stream().mapToInt(List::size).sum(), byMod.size());
        }
        return soldiers;
    }

    @SuppressWarnings("unchecked")
    private static boolean soldierHealth(EntityType<?> type) {
        try {
            var attributes = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(
                (EntityType<? extends net.minecraft.world.entity.LivingEntity>) type);
            return attributes != null && attributes.getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) <= MAX_SOLDIER_HEALTH;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * How much fight each loot chest of the dungeon at {@code pos} stands for: garrison size times its mobs' average
     * health in hearts-of-a-zombie (20 health = 1), divided by its chests. A crypt of 9 zombies and 2 chests is ~4.5; a
     * sky fortress of 100 tough mobs and 4 chests ~50; a sky village of 30 zombies and 10 chests ~3. 0: not a known
     * garrison yet.
     */
    public static double dangerPerChest(ServerLevel level, BlockPos pos) {
        for (Dungeon dungeon : dungeonsAt(level, pos)) {
            if (!inPiece(level, dungeon, pos)) {
                continue;
            }
            GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
            if (record == null || record.total <= 0 || record.mobs.isEmpty()) {
                return 0;
            }
            double weight = 0, health = 0;
            for (Map.Entry<String, Integer> mob : record.mobs.entrySet()) {
                weight += mob.getValue();
                health += mob.getValue() * baseHealth(mob.getKey());
            }
            return record.total * (health / Math.max(1, weight) / 20) / Math.max(1, record.chests);
        }
        return 0;
    }

    /** The mean health of the garrison of the dungeon at {@code pos} (by its mobs' shares), or 20 (a zombie's) if none. */
    public static double garrisonHealth(ServerLevel level, BlockPos pos) {
        for (Dungeon dungeon : dungeonsAt(level, pos)) {
            if (!inPiece(level, dungeon, pos)) {
                continue;
            }
            GarrisonRecord record = GarrisonData.get(level).getOrCreate(dungeon.key());
            Map<String, Integer> mobs = mobWeights(level, record, dungeon, pos);
            double weight = 0, health = 0;
            for (Map.Entry<String, Integer> mob : mobs.entrySet()) {
                weight += mob.getValue();
                health += mob.getValue() * baseHealth(mob.getKey());
            }
            return weight == 0 ? 20 : health / weight;
        }
        return 20;
    }

    /** The mod of the dungeon at {@code pos} (its structure's namespace), or "". */
    /** The key of the dungeon whose piece covers a position, or "" (a chest's dungeon: its loot theme). */
    public static String dungeonKeyAt(ServerLevel level, BlockPos pos) {
        return dungeonsAt(level, pos).stream().filter(dungeon -> inPiece(level, dungeon, pos)).findFirst().map(Dungeon::key).orElse("");
    }

    public static String dungeonModAt(ServerLevel level, BlockPos pos) {
        return dungeonsAt(level, pos).stream().filter(dungeon -> inPiece(level, dungeon, pos)).findFirst()
            .map(dungeon -> dungeon.id().contains(":") ? dungeon.id().substring(0, dungeon.id().indexOf(':')) : "").orElse("");
    }

    private static final Map<String, Double> HEALTH = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static double baseHealth(String id) {
        return HEALTH.computeIfAbsent(id, key -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getOptional(net.minecraft.resources.ResourceLocation.parse(key)).map(type -> {
                try {
                    var attributes = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(
                        (EntityType<? extends net.minecraft.world.entity.LivingEntity>) type);
                    return attributes.getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                } catch (RuntimeException e) {
                    return 20.0;
                }
            }).orElse(20.0));
    }

    private static boolean isSoldier(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && soldiers().getOrDefault(id.substring(0, colon), List.of()).contains(id);
    }

    /**
     * A dungeon's garrison, fixed when it first starts: the core (spawners, else the structure's monster spawns, else
     * the biome's) scaled to {@link #CORE_SHARE}, joined by up to {@link #KIN} monsters of the structure's own mod and
     * {@link #LOCALS} modded monsters of the biome; a plain core (zombies, skeletons, spiders) gives up half its share.
     * The picks are the dungeon's own (seeded by its key): two crypts of one mod still differ. A story dungeon's
     * garrison list (its tier and theme) takes the place of kin and locals.
     */
    public static Map<String, Integer> mobWeights(ServerLevel level, GarrisonRecord record, Dungeon dungeon, BlockPos pos) {
        List<String> listed = net.expedition.route.Route.garrisonFor(level.getServer(), dungeon.key()).stream()
            .filter(id -> EntityType.byString(id).isPresent()).toList();
        // A garrison fixed before its dungeon had a list (an older world) takes the list: none of its kinds is on it.
        if (!record.mobs.isEmpty() && (listed.isEmpty() || listed.stream().anyMatch(record.mobs::containsKey))) {
            return record.mobs;
        }
        boolean migrated = !record.mobs.isEmpty();
        record.mobs.clear();
        Map<String, Integer> biome = weights(level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap());
        biome.keySet().removeIf(id -> !isSoldier(id));
        Map<String, Integer> core = !record.spawnerTypes.isEmpty() ? new LinkedHashMap<>(record.spawnerTypes) : structureMonsters(dungeon.structure());
        core = new LinkedHashMap<>(core);
        core.keySet().removeIf(id -> NOT_GARRISON.contains(id) || NOT_SOLDIER.matcher(id).find());
        if (core.isEmpty()) {
            core = new LinkedHashMap<>(biome);
            core.keySet().removeIf(id -> !id.startsWith("minecraft:"));
        }
        if (core.isEmpty()) {
            core = new LinkedHashMap<>(FALLBACK_MOBS);
        }
        java.util.Random random = new java.util.Random(dungeon.key().hashCode());
        String mod = dungeon.id().contains(":") ? dungeon.id().substring(0, dungeon.id().indexOf(':')) : "minecraft";
        List<String> kin = new ArrayList<>(mod.equals("minecraft") ? List.of() : soldiers().getOrDefault(mod, List.of()));
        List<String> locals = new ArrayList<>(biome.keySet());
        locals.removeIf(id -> id.startsWith("minecraft:") || kin.contains(id));
        Map<String, Integer> joiners = new LinkedHashMap<>();
        java.util.Collections.shuffle(kin, random);
        java.util.Collections.shuffle(locals, random);
        if (!listed.isEmpty()) {
            // A miniboss among them (over a soldier's health) comes rarely.
            listed.forEach(id -> joiners.put(id, baseHealth(id) > MAX_SOLDIER_HEALTH ? 1 : JOINER_WEIGHT));
        } else {
            kin.stream().limit(KIN).forEach(id -> joiners.put(id, JOINER_WEIGHT));
            locals.stream().filter(id -> !joiners.containsKey(id)).limit(LOCALS).forEach(id -> joiners.put(id, JOINER_WEIGHT));
        }
        joiners.keySet().removeAll(core.keySet());
        int coreTotal = core.values().stream().mapToInt(Integer::intValue).sum();
        boolean plain = PLAIN.containsAll(core.keySet());
        double share = (plain && !joiners.isEmpty() ? CORE_SHARE / 2.0 : CORE_SHARE) / Math.max(1, coreTotal);
        Map<String, Integer> result = new LinkedHashMap<>();
        core.forEach((id, weight) -> result.put(id, Math.max(1, (int) Math.round(weight * share))));
        result.putAll(joiners);
        if (migrated) {
            record.mobs.putAll(result);
            Expedition.LOG.info("Garrison {}: takes its dungeon's list {}", dungeon.key(), result.keySet());
        }
        return result;
    }

    private static String pickWeighted(ServerLevel level, Map<String, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = level.random.nextInt(Math.max(1, total));
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    public static boolean isFloor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockPos head = pos.above();
        BlockState feet = level.getBlockState(pos);
        BlockState top = level.getBlockState(head);
        return feet.getCollisionShape(level, pos).isEmpty() && feet.getFluidState().isEmpty()
            && top.getCollisionShape(level, head).isEmpty() && top.getFluidState().isEmpty()
            && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static BlockPos floorNear(ServerLevel level, int x, int y, int z, int maxDy) {
        for (int dy = 0; dy <= maxDy; dy++) {
            BlockPos up = new BlockPos(x, y + dy, z);
            if (isFloor(level, up)) {
                return up;
            }
            BlockPos down = new BlockPos(x, y - dy, z);
            if (dy > 0 && isFloor(level, down)) {
                return down;
            }
        }
        return null;
    }

    static BlockPos randomFloorNear(ServerLevel level, BlockPos center, int radius) {
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = floorNear(level,
                center.getX() + level.random.nextInt(radius * 2 + 1) - radius,
                center.getY(),
                center.getZ() + level.random.nextInt(radius * 2 + 1) - radius, 3);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    static boolean canSee(ServerLevel level, Player player, BlockPos pos) {
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        return level.clip(new ClipContext(player.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            .getType() == HitResult.Type.MISS;
    }

    /**
     * Where the player stands, or the floor under them while flying (creative): spawn spots are searched around their
     * height, and a flying player would otherwise find none.
     */
    static BlockPos standingPos(ServerLevel level, Player player) {
        if (player.onGround()) {
            return player.blockPosition();
        }
        var hit = level.clip(new ClipContext(player.position(), player.position().subtract(0, 48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? player.blockPosition() : BlockPos.containing(hit.getLocation()).above();
    }

    private static BlockPos spotAroundPlayer(ServerLevel level, Player player) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double distance = SPAWN_MIN_DISTANCE + level.random.nextDouble() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE);
        BlockPos origin = standingPos(level, player);
        return floorNear(level, (int) Math.floor(origin.getX() + Math.cos(angle) * distance), origin.getY(),
            (int) Math.floor(origin.getZ() + Math.sin(angle) * distance), SPAWN_MAX_HEIGHT_DIFF);
    }

    /**
     * A spawn spot around the player (8-20 blocks, about their height), in a piece of the dungeon if possible, else
     * anywhere in its frame; hidden from the player if possible, otherwise far enough. Spawner positions don't matter.
     */
    static BlockPos findSpawnSpot(ServerLevel level, Dungeon dungeon, GarrisonRecord record, Player player, boolean allowVisible) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos pos = spotAroundPlayer(level, player);
            if (pos == null || !dungeon.box().isInside(pos) || attempt < 10 && !inPiece(level, dungeon, pos)) {
                continue;
            }
            double distance = Math.sqrt(player.distanceToSqr(Vec3.atBottomCenterOf(pos)));
            if (distance < SPAWN_MIN_DISTANCE || distance > SPAWN_MAX_DISTANCE + SPAWN_MAX_HEIGHT_DIFF) {
                continue;
            }
            if (!canSee(level, player, pos) || (allowVisible && distance >= VISIBLE_SPAWN_MIN_DISTANCE)) {
                return pos;
            }
        }
        return null;
    }

    private static final Map<String, Boolean> SWIMS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A mob that only moves in water (guardians, Aquamirae's fish): it comes in the dungeon's water, not on its floors. */
    public static boolean swims(ServerLevel level, String id) {
        return SWIMS.computeIfAbsent(id, key -> {
            var entity = EntityType.byString(key).map(type -> type.create(level)).orElse(null);
            // Not those that walk too (the drowned): a swimmer has no ground navigation and spawns in water or lives in it.
            boolean water = entity instanceof Mob mob && !(mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation)
                && (mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation || mob instanceof net.minecraft.world.entity.animal.WaterAnimal
                    || net.minecraft.world.entity.SpawnPlacements.getPlacementType(mob.getType()) == net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER);
            if (entity != null) {
                entity.discard();
            }
            return water;
        });
    }

    /** Water two blocks deep in the dungeon near the player (in the spawn ring), or null: then none comes this time. */
    static BlockPos waterSpot(ServerLevel level, Dungeon dungeon, Player player) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = SPAWN_MIN_DISTANCE + level.random.nextDouble() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE);
            BlockPos pos = BlockPos.containing(player.getX() + Math.cos(angle) * distance,
                player.getY() + level.random.nextInt(SPAWN_MAX_HEIGHT_DIFF * 2 + 1) - SPAWN_MAX_HEIGHT_DIFF, player.getZ() + Math.sin(angle) * distance);
            if (level.isLoaded(pos) && dungeon.box().inflatedBy(8).isInside(pos) && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                && level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)) {
                return pos;
            }
        }
        return null;
    }

    /** Last resort for small dungeons (pieces narrower than the spawn ring): a floor in a piece near the player, about their height. */
    static BlockPos spotInPieces(ServerLevel level, Dungeon dungeon, Player player) {
        var pieces = dungeon.start().getPieces();
        for (int attempt = 0; attempt < 16; attempt++) {
            BoundingBox box = pieces.get(level.random.nextInt(pieces.size())).getBoundingBox();
            BlockPos pos = floorNear(level, box.minX() + level.random.nextInt(box.getXSpan()), (box.minY() + box.maxY()) / 2,
                box.minZ() + level.random.nextInt(box.getZSpan()), box.getYSpan() / 2 + 1);
            if (pos != null && box.isInside(pos) && player.distanceToSqr(Vec3.atBottomCenterOf(pos)) >= SMALL_DUNGEON_MIN_DISTANCE * SMALL_DUNGEON_MIN_DISTANCE
                && Math.abs(pos.getY() - standingPos(level, player).getY()) <= SPAWN_MAX_HEIGHT_DIFF * 2
                && player.distanceToSqr(Vec3.atBottomCenterOf(pos)) <= 4.0 * SPAWN_MAX_DISTANCE * SPAWN_MAX_DISTANCE) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Spawns one garrison mob; returns it, or null if the type can't be spawned there. No ranks: a tougher foe is a
     * tougher kind of mob (the dungeon's garrison list).
     */
    public static Mob spawnMob(ServerLevel level, String typeId, BlockPos pos, String key, int ng) {
        EntityType<?> type = EntityType.byString(typeId).orElse(null);
        if (type == null || !(type.create(level) instanceof Mob mob)) {
            Expedition.LOG.warn("Garrison: can't spawn {}", typeId);
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
        mob.setPersistenceRequired();
        mob.getPersistentData().putString(TAG, key);
        // Undead garrisons hold the dungeon in daylight: a helmet keeps them from burning (never dropped).
        if (mob.getType().is(EntityTypeTags.UNDEAD) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0);
        }
        if (ng > 0) {
            strengthen(mob, ng);
        }
        return level.addFreshEntity(mob) ? mob : null;
    }

    /** NG+: more health and damage for every reset of the dungeon. */
    static void strengthen(Mob mob, int ng) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addPermanentModifier(new AttributeModifier(NG_HEALTH, NG_HEALTH_BONUS * ng, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addPermanentModifier(new AttributeModifier(NG_DAMAGE, NG_DAMAGE_BONUS * ng, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    static boolean start(ServerLevel level, Dungeon dungeon, GarrisonRecord record, ServerPlayer player) {
        if (!isEligible(record, dungeon)) {
            return false;
        }
        scanDungeon(level, dungeon); // count spawners and chests of the whole loaded dungeon, not just the player's corner
        long area = floorArea(level, dungeon);
        int budget = budgetFor(area, record.spawners, record.chests);
        if (record.mobs.isEmpty()) {
            // Fixed once: spawners, else the structure's own monsters, else the biome where the dungeon was entered.
            record.mobs.putAll(mobWeights(level, record, dungeon, player.blockPosition()));
        }
        record.state = GarrisonRecord.State.ACTIVE;
        record.total = (int) Math.round(budget * (1 + NG_BUDGET * record.ng));
        record.killed = 0;
        record.sized = true;
        Expedition.LOG.info("Garrison {}: {} to kill (floor {}, spawners {}, chests {})", dungeon.key(), record.total, area, record.spawners, record.chests);
        return true;
    }

    /**
     * Walkable floor cells (a mob fits, solid ground below) in the dungeon's pieces: its real size, unlike piece boxes,
     * which for an underground dungeon are mostly rock. Sampled every 2nd column; chunks not loaded yet are estimated
     * from the loaded part.
     */
    public static long floorArea(ServerLevel level, Dungeon dungeon) {
        long floors = 0;
        long sampled = 0;
        long all = 0;
        for (var piece : dungeon.start().getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x += 2) {
                for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
                    all++;
                    if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                        continue;
                    }
                    sampled++;
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        if (isFloor(level, new BlockPos(x, y, z))) {
                            floors++;
                        }
                    }
                }
            }
        }
        return sampled == 0 ? all : floors * 4 * all / sampled;
    }

    public static int budgetFor(long floorArea, int spawners, int chests) {
        double side = Math.sqrt(floorArea);
        int budget = (int) Math.round(side * BUDGET_PER_SQRT_AREA * (1 + side / BUDGET_LARGE_SCALE))
            + BUDGET_PER_SPAWNER * Math.min(spawners, BUDGET_SPAWNERS_COUNTED) + BUDGET_PER_CHEST * Math.min(chests, BUDGET_CHESTS_COUNTED);
        return Math.max(BUDGET_MIN, Math.min(BUDGET_MAX, budget));
    }

    /** A garrison started under the old per-piece formula (a small spider dungeon got 48): resize it, keeping its kills. */
    static void resize(ServerLevel level, Dungeon dungeon, GarrisonRecord record) {
        int total = (int) Math.round(budgetFor(floorArea(level, dungeon), record.spawners, record.chests) * (1 + NG_BUDGET * record.ng));
        if (total < record.total) {
            record.total = Math.max(total, record.killed + 1);
        }
        record.sized = true;
        GarrisonData.get(level).setDirty();
        Expedition.LOG.info("Garrison {}: resized to {} ({} killed)", dungeon.key(), record.total, record.killed);
    }

    static void spawnWave(ServerLevel level, Dungeon dungeon, GarrisonRecord record, ServerPlayer player) {
        Map<String, Integer> weights = mobWeights(level, record, dungeon, player.blockPosition());
        if (weights.isEmpty()) {
            return;
        }
        // Each player gets their own share around them: as many mobs near them as one player alone would face, times the
        // players standing with them (two apart: each their own; two together: mobs for two). The garrison's number
        // stays the same, so together it falls sooner, but it isn't any softer.
        AABB near = player.getBoundingBox().inflate(PLAYER_SHARE_RADIUS);
        int company = (int) Math.min(MAX_PLAYERS_COUNTED, level.players().stream().filter(other -> !other.isSpectator() && near.contains(other.position())).count());
        List<Mob> mine = level.getEntitiesOfClass(Mob.class, near, mob -> mob.isAlive() && dungeon.key().equals(mob.getPersistentData().getString(TAG)));
        // The pressure stays the same to the end: as many mobs around as at the start until the bar is empty, however
        // few kills are left (the extra ones go when it empties).
        long room = Math.min(SPAWN_PER_WAVE, (long) maxAlive(record) * Math.max(1, company) - mine.size());
        for (int i = 0; i < room; i++) {
            String type = pickWeighted(level, weights);
            BlockPos pos;
            if (swims(level, type)) {
                pos = waterSpot(level, dungeon, player);
            } else {
                pos = findSpawnSpot(level, dungeon, record, player, false);
                if (pos == null) {
                    pos = findSpawnSpot(level, dungeon, record, player, true);
                }
                if (pos == null) {
                    pos = spotInPieces(level, dungeon, player);
                }
            }
            if (pos != null && spawnMob(level, type, pos, dungeon.key(), record.ng) != null) {
                record.alive++;
            }
        }
    }

    // ---------------------------------------------------------------- clearing

    static void tryClear(ServerLevel level, String key, GarrisonRecord record, BoundingBox box) {
        if (record.state != GarrisonRecord.State.ACTIVE || record.remaining() > 0) {
            return;
        }
        record.state = GarrisonRecord.State.CLEARED;
        GarrisonBars.remove(key);
        Expedition.LOG.info("Garrison {}: cleared", key);
        // The rest of the garrison (the ones still about when the bar emptied, stragglers elsewhere) is gone with it.
        for (Mob mob : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class),
            mob -> mob.isAlive() && key.equals(mob.getPersistentData().getString(TAG)))) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            mob.discard();
        }
        Route.onDungeonCleared(level, key, box);
        if (box != null) {
            BoundingBox area = box.inflatedBy(64);
            for (ServerPlayer player : level.players()) {
                if (area.isInside(player.blockPosition())) {
                    player.displayClientMessage(Component.translatable("expedition.garrison.cleared").withStyle(ChatFormatting.GREEN), true);
                }
            }
        }
    }

    // ---------------------------------------------------------------- per-player tick

    /** One garrison step for a player: scan, start, spawn. Public for tests and commands. */
    public static void tickPlayer(ServerLevel level, ServerPlayer player, long tick) {
        scanAround(level, player);
        GarrisonData data = GarrisonData.get(level);
        BlockPos pos = player.blockPosition();
        for (Dungeon dungeon : dungeonsNear(level, pos, ACTIVATION_MARGIN)) {
            GarrisonRecord record = data.getOrCreate(dungeon.key());
            if (record.state == GarrisonRecord.State.CLEARED) {
                continue;
            }
            if (record.state == GarrisonRecord.State.PENDING && !start(level, dungeon, record, player)) {
                continue;
            }
            if (!record.sized) {
                resize(level, dungeon, record);
            }
            GarrisonBars.show(dungeon.key(), record, player);
            if (tick % SPAWN_INTERVAL == 0 && record.remaining() > 0) {
                spawnWave(level, dungeon, record, player);
            }
        }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick % SCAN_INTERVAL != 0) {
            return;
        }
        GarrisonBars.beginTick();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (!player.isSpectator()) {
                    tickPlayer(level, player, tick);
                }
            }
        }
        GarrisonBars.endTick();
    }

    /** A garrison mob removed without dying (discarded by a mod, despawned): no kill, it is simply no longer about. */
    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
            return;
        }
        String key = entity.getPersistentData().getString(TAG);
        GarrisonRecord record = key.isEmpty() ? null : GarrisonData.get(level).find(key);
        if (record != null && record.state == GarrisonRecord.State.ACTIVE && record.alive > 0) {
            record.alive--;
            GarrisonData.get(level).setDirty();
        }
    }

    /** No villagers, traders or golems inside a dungeon that still has its garrison (only vanilla villages are peaceful). */
    @SubscribeEvent
    static void noVillagersInDungeons(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || !(entity instanceof net.minecraft.world.entity.npc.AbstractVillager
            || entity instanceof net.minecraft.world.entity.animal.IronGolem golem && !golem.isPlayerCreated())) {
            return;
        }
        BlockPos pos = entity.blockPosition();
        for (Dungeon dungeon : dungeonsAt(level, pos)) {
            GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
            if (dungeon.box().isInside(pos) && (record == null || record.state != GarrisonRecord.State.CLEARED)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        String key = entity.getPersistentData().getString(TAG);
        if (key.isEmpty() || key.equals(MONSTER_ROOM)) {
            return;
        }
        GarrisonData data = GarrisonData.get(level);
        GarrisonRecord record = data.find(key);
        if (record == null || record.state != GarrisonRecord.State.ACTIVE) {
            return;
        }
        record.alive = Math.max(0, record.alive - 1);
        record.killed++;
        data.setDirty();
        Dungeon owner = dungeonsAt(level, entity.blockPosition()).stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
        tryClear(level, key, record, owner == null ? null : owner.box());
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative() || event.getState().getDestroySpeed(level, event.getPos()) == 0) {
            return;
        }
        if (isGuarded(level, event.getPos())) {
            event.getPlayer().displayClientMessage(Component.translatable("expedition.garrison.protected").withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
        }
    }

    /**
     * No block against a locked one of an uncleared dungeon: set against it, the wall would count as the outer layer and
     * be broken through (isGuarded). Anywhere else (a tunnel dug through the rock) building is fine, and so are blocks
     * without collision (torches, rails, flowers): they open nothing.
     */
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        BlockPos pos = event.getPos();
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player) || player.isCreative()
            || !DungeonBlocks.opens(level, pos, event.getPlacedBlock())) {
            return;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            // The block is already set when this fires: the neighbour is judged as it was without it.
            if (DungeonBlocks.placed(level, next) && !DungeonBlocks.onShell(level, next, pos) && isProtected(level, next)) {
                player.displayClientMessage(Component.translatable("expedition.garrison.protected").withStyle(ChatFormatting.RED), true);
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Buckets neither pour nor scoop inside an uncleared dungeon (lava or water could wall off a corridor for good). */
    @SubscribeEvent
    static void onBucket(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || player.isCreative()
            || !(event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem)) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0, true);
        BlockPos target = hit instanceof net.minecraft.world.phys.BlockHitResult block ? block.getBlockPos() : player.blockPosition();
        if (isProtected(level, target) || isProtected(level, player.blockPosition())) {
            player.displayClientMessage(Component.translatable("expedition.garrison.protected").withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Dungeon> dungeons = dungeonsAt(level, BlockPos.containing(event.getExplosion().center()));
        if (!dungeons.isEmpty()) {
            event.getAffectedBlocks().removeIf(pos -> guardedBlock(level, pos) && isProtected(level, pos, dungeons));
        }
    }

    /**
     * Doors stop nothing: hostile ground mobs path through wooden doors and open them (iron doors still need redstone).
     * Garrison mobs always hunt players.
     */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) {
            return;
        }
        // Ranks are gone: an elite or captain of an older world becomes its kind again (name and strength).
        if (mob.getPersistentData().contains(Expedition.MOD_ID + ":rank")) {
            mob.getPersistentData().remove(Expedition.MOD_ID + ":rank");
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
            for (String id : List.of("rank_health", "rank_damage")) {
                ResourceLocation modifier = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, id);
                for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
                    AttributeInstance instance = mob.getAttribute(attribute);
                    if (instance != null) {
                        instance.removeModifier(modifier);
                    }
                }
            }
            mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
        }
        if (mob.getNavigation() instanceof GroundPathNavigation navigation && !navigation.canOpenDoors()) {
            navigation.setCanOpenDoors(true);
            mob.goalSelector.addGoal(2, new OpenDoorGoal(mob, false));
        }
        // A garrison defends its dungeon day and night: it hunts players whatever the light (a spider's own goals give up
        // in daylight, so it also gets a plain melee attack that doesn't).
        if (!mob.getPersistentData().getString(TAG).isEmpty()) {
            mob.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob,
                net.minecraft.world.entity.player.Player.class, false));
            if (mob instanceof net.minecraft.world.entity.monster.Spider spider) {
                spider.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(spider, 1.0, true));
            }
        }
    }
}
