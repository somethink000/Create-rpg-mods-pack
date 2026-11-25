package net.expedition.test;

import java.util.List;
import net.minecraft.world.item.Items;
import java.util.Optional;
import java.util.UUID;
import net.expedition.Expedition;
import net.expedition.ExpeditionRegistry;
import net.expedition.garrison.Garrisons;
import net.expedition.route.Route;
import net.expedition.route.RouteData;
import net.expedition.route.RoutePlacer;
import net.expedition.route.RouteStep;
import net.expedition.route.RouteSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Route flow with a mock player: open journal → dungeon placed far away → cleared → boss step → finished. */
@GameTestHolder(Expedition.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteTests {
    private RouteTests() {
    }

    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    /** A finished placement's callback requesting the next one (route chains, testall) must not break the tick loop. */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void chainedPlacementRequests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(BlockPos.ZERO);
        List<ResourceLocation> outpost = List.of(ResourceLocation.withDefaultNamespace("pillager_outpost"));
        int[] placed = {0};
        RoutePlacer.request(level, outpost, center, 300, 500, first -> {
            helper.assertTrue(first != null, "first placement failed");
            placed[0]++;
            RoutePlacer.request(level, outpost, center, 600, 800, second -> {
                helper.assertTrue(second != null, "chained placement failed");
                placed[0]++;
            });
        });
        helper.succeedWhen(() -> helper.assertTrue(placed[0] == 2, "placed " + placed[0] + " of 2"));
    }

    /**
     * A boss in a closed underground hall (the Wroughtnaut's chamber): the camera's whole way stays inside the hall, clear
     * of every block, and sees the boss; in solid rock there is no shot at all (the scene is then only heard).
     */
    @GameTest(template = "empty")
    public static void bossShotStaysInsideItsHall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(0, 30, 0));
        BlockPos.betweenClosed(corner, corner.offset(14, 9, 14)).forEach(pos -> level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2));
        BlockPos.betweenClosed(corner.offset(1, 1, 1), corner.offset(13, 6, 13)).forEach(pos -> level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2));
        Mob boss = EntityType.ZOMBIE.create(level);
        boss.moveTo(corner.getX() + 7.5, corner.getY() + 1, corner.getZ() + 7.5);
        net.expedition.route.Shots.Path shot = net.expedition.route.Shots.ofEntity(level, boss);
        helper.assertTrue(shot != null, "no shot in an open hall");
        net.minecraft.world.phys.AABB hall = new net.minecraft.world.phys.AABB(corner.offset(1, 1, 1).getCenter().subtract(0.1, 0.1, 0.1),
            corner.offset(13, 6, 13).getCenter().add(0.1, 0.1, 0.1));
        helper.assertTrue(hall.contains(shot.from()) && hall.contains(shot.to()), "camera out of the hall: " + shot);
        BlockPos.betweenClosed(corner.offset(1, 1, 1), corner.offset(13, 6, 13)).forEach(pos -> level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2));
        helper.assertTrue(net.expedition.route.Shots.ofEntity(level, boss) == null, "a shot inside solid rock");
        helper.succeed();
    }

    /**
     * A dungeon without a boss of its own gets a guardian: a zombie made into a boss (its health, size, name and mark),
     * and only its death completes the step, its place kept for every player's map.
     */
    /** A boss the dungeon placed itself (the monument's elder guardian) becomes the step's guardian: no second one rises. */
    @GameTest(template = "empty")
    public static void dungeonsOwnBossBecomesTheGuardian(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        RouteData.Target target = new RouteData.Target();
        target.dimension = "minecraft:overworld";
        target.pos = at;
        target.dungeonKey = "minecraft:monument|0,0";
        var own = net.minecraft.world.entity.EntityType.ELDER_GUARDIAN.spawn(level, at, net.minecraft.world.entity.MobSpawnType.STRUCTURE);
        RouteStep step = new RouteStep(1, "T0", List.of(), OVERWORLD, 300, 500, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), false, false, "",
            List.of(), Optional.of(new RouteStep.Guardian(ResourceLocation.withDefaultNamespace("elder_guardian"), 0, 0, 1.0, 0, true)));
        var guardian = net.expedition.route.Guardians.rise(level, "test_adopted", step, target);
        helper.assertTrue(guardian == own && "test_adopted".equals(net.expedition.route.Guardians.stepOf(own)) && !own.hasCustomName(),
            "the dungeon's own boss was not adopted: " + guardian);
        own.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardianRisesAndItsDeathCompletesTheStep(GameTestHelper helper) {
        List<RouteSteps.Entry> previous = RouteSteps.all();
        RouteSteps.setForTests(List.of(new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_guarded"),
            new RouteStep(1, "T0", List.of(ResourceLocation.withDefaultNamespace("pillager_outpost")), OVERWORLD, 300, 500, Optional.empty(), List.of(), List.of(),
                Optional.empty(), List.of(), false, false, "", List.of(), Optional.of(new RouteStep.Guardian(ResourceLocation.withDefaultNamespace("zombie"), 60, 6, 1.5, 4, true))))));
        Route.resetForTests();
        try {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Route.setBranchForTests(player, 901);
            RouteData.get(helper.getLevel().getServer()).all().remove(Route.journalOf(player)); // the test world keeps its data between runs
            BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
            player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            UUID id = Route.journalOf(player);
            RouteData data = RouteData.get(level.getServer());
            RouteData.Progress progress = data.progress(id);
            progress.act = 1;
            RouteData.Target target = new RouteData.Target();
            target.dimension = "minecraft:overworld";
            target.pos = at;
            target.dungeonKey = "minecraft:pillager_outpost|0,0";
            progress.active.put("test_guarded", target);
            var guardian = net.expedition.route.Guardians.rise(level, "test_guarded", RouteSteps.all().get(0).step(), target);
            helper.assertTrue(guardian instanceof net.minecraft.world.entity.monster.Zombie, "no zombie guardian: " + guardian);
            helper.assertTrue(guardian.getMaxHealth() == 60 && guardian.getScale() > 1.4 && "test_guarded".equals(net.expedition.route.Guardians.stepOf(guardian))
                && guardian.hasCustomName(), "guardian not made a boss: health " + guardian.getMaxHealth() + " scale " + guardian.getScale());
            helper.assertTrue(net.expedition.route.Guardians.rise(level, "test_guarded", RouteSteps.all().get(0).step(), target) == guardian, "a second guardian rose");
            // No blast (a cannon's, a missile's) takes more than a small share of a boss's health.
            guardian.invulnerableTime = 0;
            guardian.hurt(level.damageSources().explosion(null, null), 1000);
            helper.assertTrue(guardian.isAlive() && guardian.getHealth() >= guardian.getMaxHealth() * 0.93f, "one blow took " + guardian.getHealth() + "/" + guardian.getMaxHealth());
            // An ordinary zombie's death completes nothing; the guardian's does.
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.spawn(level, at, net.minecraft.world.entity.MobSpawnType.EVENT);
            zombie.kill();
            helper.assertTrue(!progress.done.contains("test_guarded"), "an ordinary zombie completed the guarded step");
            guardian.kill();
            helper.assertTrue(progress.done.contains("test_guarded"), "the guardian's death did not complete the step");
            // No shard: where it fell is on the player's map instead (their own landmark: shown though never explored).
            helper.assertTrue(progress.fallen.containsKey("test_guarded"), "the fallen guardian's place was not kept");
            var landmarks = folk.sisby.surveyor.landmark.WorldLandmarks.of(level);
            helper.assertTrue(landmarks == null || landmarks.contains(folk.sisby.surveyor.Surveyor.getUuid(player),
                ResourceLocation.fromNamespaceAndPath("antique_atlas", "custom/skull/expedition/fallen/test_guarded")), "the fallen guardian is not on the map");
        } finally {
            RouteSteps.setForTests(previous);
            Route.resetForTests();
        }
        helper.succeed();
    }

    /** One story for all: two players share a journal, and a player left on a branch of the old Sprout comes back on login. */
    @GameTest(template = "empty")
    public static void everyoneSharesOneStory(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(Route.journalOf(first).equals(Route.journalOf(second)) && Route.branchOf(first) == 0, "two players have different journals");
        helper.succeed();
    }

    /** A boss's blow takes at most a third of a player's health in the chapter's armor (three blows), more without it. */
    @GameTest(template = "empty")
    public static void bossBlowsAreCappedByChapterArmor(GameTestHelper helper) {
        List<RouteSteps.Entry> previous = RouteSteps.all();
        RouteSteps.setForTests(List.of(new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_blow"),
            new RouteStep(1, "T0", List.of(), OVERWORLD, 0, 0, Optional.of(ResourceLocation.withDefaultNamespace("ravager")), List.of(), List.of(),
                Optional.empty(), List.of(), false, false, "", List.of(), Optional.empty()))));
        try {
            ServerLevel level = helper.getLevel();
            var boss = net.minecraft.world.entity.EntityType.RAVAGER.create(level);
            boss.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 1, 1)));
            level.addFreshEntity(boss);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.hurt(level.damageSources().mobAttack(boss), 100);
            helper.assertTrue(player.getHealth() >= 20 * (1 - 0.6f) - 0.01f, "an unarmored player lost " + (20 - player.getHealth()));
            player.setHealth(20);
            player.invulnerableTime = 0;
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                player.setItemSlot(slot, new ItemStack(switch (slot) {
                    case HEAD -> Items.IRON_HELMET;
                    case CHEST -> Items.IRON_CHESTPLATE;
                    case LEGS -> Items.IRON_LEGGINGS;
                    default -> Items.IRON_BOOTS;
                }));
            }
            player.hurt(level.damageSources().mobAttack(boss), 100);
            helper.assertTrue(player.getHealth() >= 20 * (1 - 0.34f) - 0.01f, "a player in iron lost " + (20 - player.getHealth()));
            boss.discard();
        } finally {
            RouteSteps.setForTests(previous);
        }
        helper.succeed();
    }

    /** A dungeon placed ahead of its act (the prologue films it) becomes the journal's dungeon when the act opens. */
    @GameTest(template = "empty")
    public static void dungeonPlacedAheadIsTakenByItsAct(GameTestHelper helper) {
        List<RouteSteps.Entry> previous = RouteSteps.all();
        RouteSteps.setForTests(List.of(new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_ahead"),
            new RouteStep(1, "T0", List.of(ResourceLocation.withDefaultNamespace("pillager_outpost")), OVERWORLD, 300, 500, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), false, false, "", List.of(), Optional.empty()))));
        Route.resetForTests();
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Route.setBranchForTests(player, 902);
            RouteData.get(helper.getLevel().getServer()).all().remove(Route.journalOf(player)); // the test world keeps its data between runs
            RouteData data = RouteData.get(helper.getLevel().getServer());
            RouteData.Target ahead = new RouteData.Target();
            ahead.dimension = "minecraft:overworld";
            ahead.pos = new BlockPos(1234, 70, -4321);
            ahead.dungeonKey = "minecraft:pillager_outpost|77,-270";
            data.reserved.put("test_ahead", ahead);
            UUID id = Route.journalOf(player);
            Route.startAct(player, id, 1);
            RouteData.Target taken = data.progress(id).active.get("test_ahead");
            helper.assertTrue(taken != null && taken.pos.equals(ahead.pos) && taken.dungeonKey.equals(ahead.dungeonKey), "the act placed its own dungeon");
            helper.assertTrue(!data.reserved.containsKey("test_ahead"), "the dungeon is still kept ahead");
        } finally {
            RouteSteps.setForTests(previous);
            Route.resetForTests();
        }
        helper.succeed();
    }

    /**
     * The story: act 1 opens (its dungeon placed, not marked), clearing the dungeon and killing the act's boss complete
     * their steps, every act's dungeon stands from the start, and with three quarters of act 1 done act 2 opens by itself.
     */
    // A batch of its own: other tests swap the route (RouteSteps.setForTests) while theirs run side by side.
    @GameTest(template = "empty", timeoutTicks = 3600, batch = "story_acts")
    public static void storyActsPlaceCompleteAndOpen(GameTestHelper helper) {
        List<RouteSteps.Entry> previous = RouteSteps.all();
        RouteSteps.setForTests(List.of(
            new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_outpost"),
                new RouteStep(1, "T0", List.of(ResourceLocation.withDefaultNamespace("pillager_outpost")), OVERWORLD, 300, 500, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), false, false, "", List.of(), Optional.empty())),
            new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_boss"),
                new RouteStep(2, "T0", List.of(), OVERWORLD, 0, 0, Optional.of(ResourceLocation.withDefaultNamespace("zombie")), List.of(), List.of(), Optional.empty(), List.of(), false, false, "", List.of(), Optional.empty())),
            new RouteSteps.Entry(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "test_next"),
                new RouteStep(3, "T1", List.of(ResourceLocation.withDefaultNamespace("pillager_outpost")), OVERWORLD, 600, 900, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), false, false, "", List.of(), Optional.empty()))));
        Route.resetForTests();

        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Route.setBranchForTests(player, 903);
        RouteData.get(helper.getLevel().getServer()).all().remove(Route.journalOf(player)); // the test world keeps its data between runs
        BlockPos start = helper.absolutePos(BlockPos.ZERO.above());
        player.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        UUID id = Route.journalOf(player);
        RouteData.Progress progress = RouteData.get(level.getServer()).progress(id);
        Route.startAct(player, id, 1);
        helper.assertTrue(progress.act == 1, "act 1 did not open");
        boolean[] cleared = {false};

        helper.succeedWhen(() -> {
            if (!cleared[0]) {
                RouteData.Target target = progress.active.get("test_outpost");
                helper.assertTrue(target != null, "act 1 dungeon not placed yet");
                BlockPos spawn = level.getSharedSpawnPos();
                double distance = Math.sqrt(Math.pow(target.pos.getX() - spawn.getX(), 2) + Math.pow(target.pos.getZ() - spawn.getZ(), 2));
                helper.assertTrue(distance > 200 && distance < 700, "dungeon at a wrong distance from spawn: " + (int) distance);
                List<Garrisons.Dungeon> found = Garrisons.dungeonsAt(level, target.pos);
                helper.assertTrue(found.stream().anyMatch(d -> d.key().equals(target.dungeonKey)), "placed dungeon not registered: " + found);
                var landmarks = folk.sisby.surveyor.landmark.WorldLandmarks.of(level);
                helper.assertTrue(landmarks == null || !landmarks.contains(folk.sisby.surveyor.Surveyor.getUuid(player),
                    net.expedition.route.RouteMarkers.id(id, "test_outpost")), "story dungeons are not marked on the map");
                Route.onDungeonCleared(level, target.dungeonKey, null);
                helper.assertTrue(progress.done.contains("test_outpost") && !progress.active.containsKey("test_outpost"),
                    "clearing the dungeon did not complete the step");
                Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos.ZERO.above());
                zombie.kill();
                helper.assertTrue(progress.done.contains("test_boss"), "killing the boss did not complete the boss step");
                cleared[0] = true;
            }
            helper.assertTrue(progress.act == 2, "act 2 did not open with act 1 done");
            helper.assertTrue(progress.active.containsKey("test_next"), "act 2 dungeon not placed yet");
            RouteSteps.setForTests(previous);
        });
    }

    /** The withered Tree at spawn: no tool, explosion, piston or drill takes it apart. */
    @GameTest(template = "empty")
    public static void witheredTreeIsUnbreakable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, net.expedition.content.StructureFacades.WITHERED_WOOD.get());
        var state = helper.getBlockState(pos);
        helper.assertTrue(state.getDestroySpeed(helper.getLevel(), helper.absolutePos(pos)) < 0, "the Tree's wood can be mined");
        helper.assertTrue(state.getBlock().getExplosionResistance() > 1000, "the Tree's wood gives to explosions");
        helper.assertTrue(state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "pistons move the Tree's wood");
        helper.getLevel().explode(null, helper.absolutePos(pos).getX() + 1.5, helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ(), 6,
            net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        helper.assertBlockPresent(net.expedition.content.StructureFacades.WITHERED_WOOD.get(), pos);
        helper.succeed();
    }

    /** Hacking at the camp Tree's wood in survival: struck dead; in creative: nothing. */
    @GameTest(template = "empty")
    public static void treeStrikesWhoeverHacksAtIt(GameTestHelper helper) {
        BlockPos wood = new BlockPos(1, 2, 1);
        helper.setBlock(wood, net.expedition.content.StructureFacades.WITHERED_WOOD.get());
        BlockPos at = helper.absolutePos(wood);
        ServerPlayer builder = helper.makeMockServerPlayerInLevel();
        builder.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
            builder, at, net.minecraft.core.Direction.UP, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START));
        helper.assertTrue(builder.isAlive(), "a creative player was struck");
        // GameTestHelper's mock player is always creative: a plain server player on a dead-end connection instead.
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "tree-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 1.5);
        var hit = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
            player, at, net.minecraft.core.Direction.UP, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START));
        helper.assertTrue(hit.isCanceled(), "the Tree didn't answer (survival " + !player.isCreative() + ", block " + helper.getLevel().getBlockState(at) + ")");
        boolean dead = player.isDeadOrDying();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(dead, "the player hacking at the Tree lives (health " + player.getHealth() + ")");
        helper.succeed();
    }
}
