package net.expedition.test;

import java.util.List;
import net.expedition.Expedition;
import net.expedition.garrison.DungeonReset;
import net.expedition.garrison.DungeonBlocks;
import net.expedition.garrison.GarrisonData;
import net.expedition.garrison.GarrisonRecord;
import net.expedition.garrison.Garrisons;
import net.expedition.world.StructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Garrison and structure placement checks with a mock player (run: ./gradlew runGameTestServer). */
@GameTestHolder(Expedition.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarrisonTests {
    private GarrisonTests() {
    }

    private static StructureStart placeOutpost(GameTestHelper helper, int offset) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> outpost = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.PILLAGER_OUTPOST);
        StructureStart start = StructurePlacer.place(level, outpost.value(), helper.absolutePos(BlockPos.ZERO).offset(offset, 0, offset));
        if (start == null) {
            helper.fail("pillager outpost did not place");
        }
        return start;
    }

    /** A floor spot inside the first piece of the structure. */
    private static BlockPos floorInside(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getPieces().get(0).getBoundingBox();
        BlockPos center = box.getCenter();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            BlockPos pos = new BlockPos(center.getX(), y, center.getZ());
            if (Garrisons.isFloor(level, pos)) {
                return pos;
            }
        }
        return center;
    }

    private static List<Mob> garrisonMobs(ServerLevel level, StructureStart start, String key) {
        return level.getEntitiesOfClass(Mob.class, AABB.of(start.getBoundingBox()).inflate(48),
            mob -> mob.isAlive() && key.equals(mob.getPersistentData().getString(Garrisons.TAG)));
    }

    /** Garrison size follows the dungeon's floor area: a shed is a handful, a big crypt a few dozen, a fort around a hundred. */
    @GameTest(template = "empty")
    public static void budgetScalesWithSize(GameTestHelper helper) {
        int shed = Garrisons.budgetFor(50, 0, 2);
        int crypt = Garrisons.budgetFor(1_200, 4, 3);
        int tower = Garrisons.budgetFor(20_000, 3, 120);
        helper.assertTrue(shed <= 6, "shed garrison " + shed);
        helper.assertTrue(crypt > shed * 2 && crypt <= 32, "crypt garrison " + crypt);
        // A five-tower fort is a long fight (~100), and a pile of decorative barrels adds at most two dozen.
        helper.assertTrue(tower >= 80 && tower <= Garrisons.BUDGET_MAX, "tower " + tower);
        helper.assertTrue(Garrisons.budgetFor(1_000, 0, 500) - Garrisons.budgetFor(1_000, 0, 0) <= Garrisons.BUDGET_CHESTS_COUNTED, "barrels");
        helper.succeed();
    }

    /**
     * A structure's own endless spawns (an outpost's pillagers) yield to the garrison: none while it holds the dungeon,
     * then only what the biome spawns anywhere.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void structureSpawnsYieldToGarrison(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 40);
        BlockPos pos = floorInside(level, start);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, pos).stream().filter(d -> d.start() == start || d.box().equals(start.getBoundingBox()))
            .findFirst().orElse(null);
        if (dungeon == null) {
            helper.fail("outpost not found at " + pos);
            return;
        }
        var override = dungeon.structure().spawnOverrides().get(net.minecraft.world.entity.MobCategory.MONSTER);
        java.util.function.Supplier<net.neoforged.neoforge.event.level.LevelEvent.PotentialSpawns> attempt = () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new net.neoforged.neoforge.event.level.LevelEvent.PotentialSpawns(level, net.minecraft.world.entity.MobCategory.MONSTER, pos, override.spawns()));
        helper.assertTrue(attempt.get().isCanceled(), "monsters spawn in an uncleared outpost");
        GarrisonData.get(level).getOrCreate(dungeon.key()).state = GarrisonRecord.State.CLEARED;
        var cleared = attempt.get();
        var biome = level.getBiome(pos).value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).unwrap();
        helper.assertFalse(cleared.isCanceled(), "nothing spawns in a cleared outpost");
        helper.assertTrue(cleared.getSpawnerDataList().equals(biome), "cleared outpost spawns " + cleared.getSpawnerDataList() + ", biome " + biome);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void placedStructureIsRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 300);
        BlockPos inside = floorInside(level, start);
        List<Garrisons.Dungeon> dungeons = Garrisons.dungeonsAt(level, inside);
        helper.assertTrue(dungeons.stream().anyMatch(d -> d.id().equals("minecraft:pillager_outpost")),
            "placed outpost is not visible to structure lookups at " + inside.toShortString() + ": " + dungeons);
        helper.succeed();
    }

    /**
     * A garrison spider hunts in daylight too (vanilla spiders turn neutral in the light): it gets a light-blind player
     * target and a melee attack that doesn't give up in the light. (Mock players are creative, so no live chase here.)
     */
    @GameTest(template = "empty")
    public static void garrisonSpidersHuntInDaylight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob spider = Garrisons.spawnMob(level, "minecraft:spider", helper.absolutePos(new BlockPos(1, 1, 1)), "test:daylight", 0);
        helper.assertTrue(spider != null, "spider not spawned");
        helper.assertTrue(spider.targetSelector.getAvailableGoals().stream()
            .anyMatch(goal -> goal.getGoal().getClass() == net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal.class),
            "garrison spider has no light-blind player target");
        helper.assertTrue(spider.goalSelector.getAvailableGoals().stream()
            .anyMatch(goal -> goal.getGoal().getClass() == net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class),
            "garrison spider has no daylight melee attack");
        spider.discard();
        helper.succeed();
    }

    /** Trial spawners (arenas, trial chambers) are farms too: removed like plain spawners and counted for the garrison. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void trialSpawnersAreRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 700);
        BlockPos inside = floorInside(level, start);
        BlockPos spawnerPos = inside.offset(1, 0, 1);
        level.setBlockAndUpdate(spawnerPos, Blocks.TRIAL_SPAWNER.defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        Garrisons.tickPlayer(level, player, 0);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
        helper.assertTrue(level.getBlockState(spawnerPos).isAir(), "trial spawner was not removed");
        helper.assertTrue(record != null && record.spawners == 1, "trial spawner not counted: " + record);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void garrisonSpawnsProtectsAndClears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 600);
        BlockPos inside = floorInside(level, start);

        // A zombie spawner inside the dungeon: must be removed and decide the garrison's mob type.
        BlockPos spawnerPos = inside.offset(1, 0, 1);
        level.setBlockAndUpdate(spawnerPos, Blocks.SPAWNER.defaultBlockState());
        if (level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(EntityType.ZOMBIE, level.random);
        }

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        Garrisons.tickPlayer(level, player, 0);

        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
        helper.assertTrue(record != null && record.state == GarrisonRecord.State.ACTIVE, "garrison did not start: " + record);
        helper.assertTrue(level.getBlockState(spawnerPos).isAir(), "spawner was not removed");
        helper.assertTrue(record.spawners == 1 && record.spawnerTypes.containsKey("minecraft:zombie"), "spawner not counted: " + record.spawnerTypes);
        helper.assertTrue(record.total > 0 && record.remaining() == record.total, "empty garrison: " + record.total);
        helper.assertTrue(Garrisons.isProtected(level, inside), "dungeon not protected while garrisoned");

        // A garrison from the old formula (oversized, 3 already killed) is resized on the next visit, kills kept.
        int fresh = record.total;
        record.sized = false;
        record.total = 100;
        record.killed = 3;
        Garrisons.tickPlayer(level, player, 1);
        helper.assertTrue(record.sized && record.total == fresh && record.killed == 3, "old garrison not resized: total=" + record.total + " killed=" + record.killed);
        record.killed = 0;

        // Fight: keep ticking and killing until the budget is spent and the last mob is dead.
        int spawned = 0;
        for (int wave = 1; wave <= 200 && record.state == GarrisonRecord.State.ACTIVE; wave++) {
            Garrisons.tickPlayer(level, player, wave * 60L);
            List<Mob> mobs = garrisonMobs(level, start, dungeon.key());
            for (Mob mob : mobs) {
                helper.assertTrue(mob.getType() == EntityType.ZOMBIE, "garrison spawned " + mob.getType() + ", expected zombies from the spawner");
                spawned++;
                mob.kill();
            }
        }
        helper.assertTrue(spawned > 0, "no garrison mob ever spawned");
        helper.assertTrue(record.state == GarrisonRecord.State.CLEARED, "not cleared after " + spawned + " kills: killed=" + record.killed + "/" + record.total);
        helper.assertFalse(Garrisons.isProtected(level, inside), "still protected after clearing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void unseenDungeonIsProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 900);
        BlockPos inside = floorInside(level, start);
        helper.assertTrue(Garrisons.isProtected(level, inside.below()), "dungeon never visited by a player is not protected");
        // Only the blocks the dungeon placed are locked: a block that came there later (rock, a tree) isn't.
        BlockPos later = inside.above();
        level.setBlockAndUpdate(later, Blocks.STONE.defaultBlockState());
        helper.assertTrue(Garrisons.isProtected(level, later), "the spot is not in the dungeon");
        helper.assertFalse(Garrisons.isGuarded(level, later), "a block the dungeon did not place is guarded");
        level.removeBlock(later, false);
        // A dungeon block is dug through where it touches the world's rock, one layer deep: the layer behind stays locked.
        BlockPos wall = null;
        BlockPos face = null;
        for (BlockPos at : BlockPos.betweenClosed(start.getBoundingBox().minX(), start.getBoundingBox().minY(), start.getBoundingBox().minZ(),
            start.getBoundingBox().maxX(), start.getBoundingBox().maxY(), start.getBoundingBox().maxZ())) {
            BlockPos up = at.above();
            if (DungeonBlocks.recorded(level, at) && DungeonBlocks.recorded(level, at.below()) && level.getBlockState(up).isAir()
                && Garrisons.isGuarded(level, at) && Garrisons.isGuarded(level, at.below())) {
                wall = at.immutable();
                face = up.immutable();
                break;
            }
        }
        helper.assertTrue(wall != null, "no locked two-layer spot in the outpost");
        level.setBlockAndUpdate(face, Blocks.STONE.defaultBlockState());
        helper.assertFalse(Garrisons.isGuarded(level, wall), "the layer touching rock is not breakable");
        helper.assertTrue(Garrisons.isGuarded(level, wall.below()), "the second layer became breakable");
        level.removeBlock(face, false);
        helper.assertTrue(Garrisons.isGuarded(level, wall), "the layer is breakable with nothing but air at its face");
        level.setBlockAndUpdate(face, Blocks.TORCH.defaultBlockState());
        helper.assertTrue(Garrisons.isGuarded(level, wall), "a torch on the wall laid it open");
        level.removeBlock(face, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void resetRestoresDungeonAndStrengthensGarrison(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 1200);
        BlockPos inside = floorInside(level, start);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        GarrisonRecord record = GarrisonData.get(level).getOrCreate(dungeon.key());
        record.state = GarrisonRecord.State.CLEARED;
        BlockPos floor = inside.below();
        var original = level.getBlockState(floor);
        helper.assertFalse(original.isAir(), "no floor to break at " + floor.toShortString());
        level.removeBlock(floor, false);

        helper.assertTrue(DungeonReset.clearedDungeonAt(level, inside) != null, "cleared dungeon not found for reset");
        DungeonReset.start(level, dungeon);
        helper.succeedWhen(() -> {
            GarrisonRecord now = GarrisonData.get(level).find(dungeon.key());
            helper.assertTrue(now.ng == 1 && now.state == GarrisonRecord.State.PENDING, "not reset yet: ng=" + now.ng + " " + now.state);
            helper.assertTrue(level.getBlockState(floor).equals(original), "broken block not restored at " + floor.toShortString());
            Mob mob = Garrisons.spawnMob(level, "minecraft:zombie", inside, dungeon.key(), now.ng);
            helper.assertTrue(mob != null && mob.getMaxHealth() > 20.0F * 1.4F, "NG+1 zombie is not stronger: " + (mob == null ? "none" : mob.getMaxHealth()));
            mob.discard();
        });
    }

    /** No ranks: a garrison mob is its kind, unnamed (a tougher foe is a tougher kind); undead get a sun helmet. */
    @GameTest(template = "empty")
    public static void garrisonMobsHaveNoRanks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob mob = Garrisons.spawnMob(level, "minecraft:zombie", helper.absolutePos(BlockPos.ZERO.above()), "test", 0);
        helper.assertTrue(!mob.hasCustomName() && !mob.getPersistentData().contains("expedition:rank"), "garrison zombie promoted: " + mob.getCustomName());
        helper.assertTrue(mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.CHAINMAIL_HELMET),
            "undead garrison mob has no sun helmet");
        mob.discard();
        // An elite of an older world loses its rank when it loads.
        var old = EntityType.ZOMBIE.create(level);
        old.moveTo(helper.absolutePos(BlockPos.ZERO.above()).getCenter());
        old.getPersistentData().putInt(Expedition.MOD_ID + ":rank", 1);
        old.setCustomName(net.minecraft.network.chat.Component.literal("Elite Zombie"));
        level.addFreshEntity(old);
        helper.assertTrue(!old.hasCustomName() && !old.getPersistentData().contains(Expedition.MOD_ID + ":rank"), "an old elite kept its rank");
        old.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newGamePlusKeepsTheSameMobs(GameTestHelper helper) {
        GarrisonRecord record = GarrisonData.get(helper.getLevel()).getOrCreate("test|ngplus|" + java.util.UUID.randomUUID());
        record.mobs.put("minecraft:husk", 3);
        record.mobs.put("minecraft:spider", 1);
        int before = record.ng;
        record.reset();
        helper.assertTrue(record.ng == before + 1 && record.mobs.equals(java.util.Map.of("minecraft:husk", 3, "minecraft:spider", 1)),
            "NG+ changed the garrison's mobs: " + record.mobs);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noVillagersInDungeons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 1400);
        BlockPos inside = floorInside(level, start);
        var villager = EntityType.VILLAGER.create(level);
        villager.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        helper.assertFalse(level.addFreshEntity(villager), "a villager appeared inside a dungeon with a garrison");
        helper.succeed();
    }

    /** A garrison mob gone without dying (discarded, despawned) is no kill: the bar stays. */
    @GameTest(template = "empty")
    public static void vanishedMobIsNoKill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GarrisonRecord record = GarrisonData.get(level).getOrCreate("test|vanish");
        record.state = GarrisonRecord.State.ACTIVE;
        record.total = 3;
        record.killed = 2;
        record.alive = 1;
        Mob mob = Garrisons.spawnMob(level, "minecraft:zombie", helper.absolutePos(BlockPos.ZERO.above()), "test|vanish", 0);
        mob.discard();
        helper.assertTrue(record.killed == 2 && record.alive == 0 && record.remaining() == 1,
            "discarded mob counted as killed: killed=" + record.killed + " alive=" + record.alive);
        helper.succeed();
    }

    /**
     * The reported stuck bar: one kill left and the last garrison mob somewhere away from the player (fallen off a
     * ship): mobs keep coming around the player, any garrison death empties the bar, and the stragglers go with it.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lastKillIsNeverStuck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 2800);
        BlockPos inside = floorInside(level, start);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        Garrisons.tickPlayer(level, player, 0);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
        record.killed = record.total - 1;
        Mob straggler = Garrisons.spawnMob(level, "minecraft:zombie", inside.offset(Garrisons.PLAYER_SHARE_RADIUS + 8, 0, 0), dungeon.key(), 0);
        helper.assertTrue(straggler != null, "no straggler");
        record.alive = 1;
        Garrisons.tickPlayer(level, player, 60);
        List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Garrisons.PLAYER_SHARE_RADIUS),
            mob -> mob.isAlive() && dungeon.key().equals(mob.getPersistentData().getString(Garrisons.TAG)));
        helper.assertTrue(!near.isEmpty(), "no mob came near the player for the last kill");
        Mob last = near.get(0);
        last.kill();
        helper.assertTrue(record.state == GarrisonRecord.State.CLEARED, "not cleared by the last kill: " + record.killed + "/" + record.total);
        helper.assertTrue(straggler.isRemoved() && near.stream().filter(mob -> mob != last).allMatch(Mob::isRemoved), "a garrison mob outlived the garrison");
        helper.succeed();
    }

    /** A story dungeon's garrison is its step's list (not random kin and locals); a miniboss on the list comes rarely. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void storyDungeonUsesItsGarrisonList(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 3400);
        BlockPos inside = floorInside(level, start);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        var previous = net.expedition.route.RouteSteps.all();
        var husk = net.minecraft.resources.ResourceLocation.withDefaultNamespace("husk");
        var ravager = net.minecraft.resources.ResourceLocation.withDefaultNamespace("ravager");
        net.expedition.route.RouteSteps.setForTests(List.of(new net.expedition.route.RouteSteps.Entry(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_listed"),
            new net.expedition.route.RouteStep(1, "T0", List.of(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"), 0, 0,
                java.util.Optional.empty(), List.of(), List.of(), java.util.Optional.empty(), List.of(), false, false, "", List.of(), java.util.Optional.empty(),
                List.of(husk, ravager)))));
        var reserved = net.expedition.route.RouteData.get(level.getServer()).reserved;
        var target = new net.expedition.route.RouteData.Target();
        target.dimension = "minecraft:overworld";
        target.pos = inside;
        target.dungeonKey = dungeon.key();
        reserved.put("test_listed", target);
        try {
            GarrisonRecord record = new GarrisonRecord();
            var weights = Garrisons.mobWeights(level, record, dungeon, inside);
            helper.assertTrue(weights.getOrDefault("minecraft:husk", 0) == 3 && weights.getOrDefault("minecraft:ravager", 0) == 1,
                "listed garrison not used: " + weights);
            helper.assertTrue(weights.keySet().stream().allMatch(id -> id.equals("minecraft:husk") || id.equals("minecraft:ravager") || id.equals("minecraft:pillager")),
                "random joiners beside the list: " + weights);
            helper.assertTrue(Garrisons.swims(level, "minecraft:guardian")
                && !Garrisons.swims(level, "minecraft:zombie") && !Garrisons.swims(level, "minecraft:drowned"), "swimmers misjudged");
        } finally {
            reserved.remove("test_listed");
            net.expedition.route.RouteSteps.setForTests(previous);
        }
        helper.succeed();
    }

    /** A boss dungeon: each section gets its mobs once (no stream around the player), and the dungeon falls with its boss. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bossDungeonHoldsSectionsAndFallsWithItsBoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = placeOutpost(helper, 4200);
        BlockPos inside = floorInside(level, start);
        Garrisons.Dungeon dungeon = Garrisons.dungeonsAt(level, inside).stream()
            .filter(d -> d.id().equals("minecraft:pillager_outpost")).findFirst().orElseThrow();
        var previous = net.expedition.route.RouteSteps.all();
        net.expedition.route.RouteSteps.setForTests(List.of(new net.expedition.route.RouteSteps.Entry(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_boss_lair"),
            new net.expedition.route.RouteStep(1, "T0", List.of(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"), 0, 0,
                java.util.Optional.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("ravager")), List.of(), List.of(), java.util.Optional.empty(),
                List.of(), false, false, "", List.of(), java.util.Optional.empty()))));
        var reserved = net.expedition.route.RouteData.get(level.getServer()).reserved;
        var target = new net.expedition.route.RouteData.Target();
        target.dimension = "minecraft:overworld";
        target.pos = inside;
        target.dungeonKey = dungeon.key();
        reserved.put("test_boss_lair", target);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
            Garrisons.tickPlayer(level, player, 0);
            GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
            helper.assertTrue(record != null && record.state == GarrisonRecord.State.ACTIVE, "garrison did not start: " + record);
            List<Mob> first = garrisonMobs(level, start, dungeon.key());
            helper.assertTrue(!first.isEmpty() && !record.sections.isEmpty(), "the section around the player got no mobs");
            first.forEach(Mob::kill);
            // Standing in the same place: the cleared section stays clear.
            for (int wave = 1; wave <= 5; wave++) {
                Garrisons.tickPlayer(level, player, wave * 60L);
            }
            helper.assertTrue(garrisonMobs(level, start, dungeon.key()).isEmpty(), "mobs came back in a cleared section");
            helper.assertTrue(record.state == GarrisonRecord.State.ACTIVE && Garrisons.isProtected(level, inside), "boss dungeon freed before its boss fell");
            Garrisons.bossFell(level, dungeon.key(), inside);
            helper.assertTrue(record.state == GarrisonRecord.State.CLEARED && !Garrisons.isProtected(level, inside), "boss fell, dungeon still held");
        } finally {
            reserved.remove("test_boss_lair");
            net.expedition.route.RouteSteps.setForTests(previous);
        }
        helper.succeed();
    }

    /** Loot follows how hard a place is, never how far it lies: far from spawn and outside dungeons is the lowest tier. */
    @GameTest(template = "empty")
    public static void lootTierIsDifficultyNotDistance(GameTestHelper helper) {
        var far = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)).add(12000, 0, 12000);
        helper.assertTrue(net.expedition.content.ChestLoot.tierDistance(helper.getLevel(), far) == 0, "a far place outside dungeons got a higher tier");
        helper.assertTrue(net.expedition.content.ChestLoot.chapterOfHealth(20) == 1 && net.expedition.content.ChestLoot.chapterOfHealth(60) == 5
            && net.expedition.content.ChestLoot.chapterOfHealth(150) == 7, "garrison health bands");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hostileMobsOpenDoors(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos.ZERO.above());
        helper.assertTrue(zombie.getNavigation() instanceof GroundPathNavigation navigation && navigation.canOpenDoors(),
            "zombie can't open doors");
        helper.succeed();
    }
}
