package net.expedition.test;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import net.expedition.Expedition;
import net.expedition.ExpeditionRegistry;
import net.expedition.content.BossLoot;
import net.expedition.content.StructureFacades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loot rule checks with real loot contexts. */
@GameTestHolder(Expedition.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootTests {
    private LootTests() {
    }

    /** Village chests (outside any dungeon) hold everyday goods only, even far out where dungeons hold epic loot. */
    @GameTest(template = "empty")
    public static void peacefulChestsAreCommon(GameTestHelper helper) {
        var everyday = new java.util.HashSet<>(net.expedition.content.DungeonLoot.pool(net.expedition.content.DungeonLoot.JUNK));
        everyday.addAll(net.expedition.content.DungeonLoot.pool(net.expedition.content.DungeonLoot.COMMON));
        var loot = net.expedition.content.DungeonLoot.generate(helper.getLevel().random, 9000, 200, net.expedition.content.DungeonLoot.COMMON);
        helper.assertTrue(!loot.isEmpty() && loot.stream().allMatch(stack -> everyday.contains(stack.getItem())),
            "valuable item in a peaceful chest: " + loot.stream().filter(stack -> !everyday.contains(stack.getItem())).findFirst());
        helper.succeed();
    }

    /** A mod's own chest table may list its mobs' drops (Aquamirae ships: angler fang): those stay with the mobs. */
    @GameTest(template = "empty")
    public static void modMobDropsStayWithMobs(GameTestHelper helper) {
        var level = helper.getLevel();
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("aquamirae:angler_fang"))
            .ifPresent(fang -> helper.assertTrue(net.expedition.content.ChestLoot.isMobDrop(level, new net.minecraft.world.item.ItemStack(fang)), "angler fang counted as chest loot"));
        helper.assertFalse(net.expedition.content.ChestLoot.isMobDrop(level, new net.minecraft.world.item.ItemStack(Items.STRING)), "string is chest loot");
        helper.succeed();
    }

    /**
     * Chests hold ingredients: no placed machine or its part (a block with a block entity), no gear, no cooked dish or
     * potion, and no special mob drop (a mod's monster or boss material).
     */
    @GameTest(template = "empty")
    public static void lootTableHasNoMadeThingsOrMobDrops(GameTestHelper helper) {
        // Special mob drops (everyday string, leather, gunpowder, slime are ingredients like any other).
        var mobDrops = new java.util.HashSet<net.minecraft.world.item.Item>(java.util.Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.BLAZE_ROD,
            Items.MAGMA_CREAM, Items.GHAST_TEAR, Items.SHULKER_SHELL, Items.PHANTOM_MEMBRANE, Items.NAUTILUS_SHELL));
        for (String id : List.of("aquamirae:angler_fang", "aquamirae:ship_graveyard_echo", "cataclysm:lacrima", "block_factorys_bosses:dragon_bone",
            "legendary_monsters:air_rune", "legendary_monsters:nature_crystal", "graveyard:corruption", "twilightforest:raven_feather",
            "twilightforest:arctic_fur", "aether:swet_ball", "deeperdarker:soul_crystal", "deeperdarker:sculk_bone")) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresent(mobDrops::add);
        }
        for (int value = 0; value <= net.expedition.content.DungeonLoot.EPIC; value++) {
            for (var item : net.expedition.content.DungeonLoot.pool(value)) {
                ItemStack stack = new ItemStack(item);
                boolean machine = item instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
                boolean dish = stack.has(net.minecraft.core.component.DataComponents.FOOD) && !(item instanceof net.minecraft.world.item.ItemNameBlockItem)
                    && item != Items.CARROT && item != Items.POTATO && item != Items.CHORUS_FRUIT && !BuiltInRegistries.ITEM.getKey(item).getPath().matches("onion|torchberries");
                helper.assertFalse(machine || net.expedition.content.ChestLoot.isGearPublic(stack) || dish || item == Items.POTION || mobDrops.contains(item),
                    "not an ingredient in the loot table: " + BuiltInRegistries.ITEM.getKey(item));
            }
        }
        helper.succeed();
    }

    /** A ship's side ladder ending 2 blocks above the sea reaches into it after generation (boarding from a boat). */
    @GameTest(template = "empty")
    public static void shipLadderReachesWater(GameTestHelper helper) {
        var level = helper.getLevel();
        // In open air above the test's barrier box: below it the world's random bedrock, inside it the barrier walls stood behind the ladder.
        BlockPos spot = helper.absolutePos(new BlockPos(1, 20, 1));
        // The plank goes behind only within the ladder's chunk (the pass sees one chunk): keep the column off its edge.
        BlockPos water = (spot.getZ() & 15) == 0 ? spot.south() : spot;
        for (int dy = -2; dy <= 0; dy++) {
            level.setBlock(water.above(dy), Blocks.WATER.defaultBlockState(), 2);
        }
        BlockPos bottom = water.above(3);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlock(bottom.north(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
        level.setBlock(bottom, ladder, 2);
        int added = StructureFacades.extendDown(level.getChunk(bottom), bottom, ladder);
        helper.assertTrue(added == 4, "expected 2 ladders above the water and 2 in it, got " + added + " column " + java.util.stream.IntStream.rangeClosed(-2, 5).mapToObj(dy -> dy + "=" + level.getBlockState(water.above(dy))).toList());
        helper.assertTrue(level.getBlockState(water.below()).is(Blocks.LADDER) && level.getBlockState(water.below()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED),
            "no waterlogged ladder under the surface");
        helper.assertTrue(StructureFacades.extendDown(level.getChunk(water.below()), water.below(), level.getBlockState(water.below())) == 0, "extended twice");
        helper.assertTrue(level.getBlockState(water.north()).is(Blocks.OAK_PLANKS), "no plank behind the extended ladder at " + water.toShortString() + ": " + level.getBlockState(water.north()));
        // A tower ladder over dry ground is left alone.
        BlockPos dry = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(dry, ladder, 2);
        helper.assertTrue(StructureFacades.extendDown(level.getChunk(dry), dry, ladder) == 0, "ladder over land extended");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chestsHoldIngredientsOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)).add(5000, 0, 0))
            .create(LootContextParamSets.CHEST);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.BREAD, 4),
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BONE, 3)));
        ObjectArrayList<ItemStack> result = new net.expedition.content.ChestLoot(new LootItemCondition[0]).apply(loot, context);
        var pool = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (int value = 0; value <= net.expedition.content.DungeonLoot.EPIC; value++) {
            pool.addAll(net.expedition.content.DungeonLoot.pool(value));
        }
        helper.assertTrue(result.size() >= 3, "too few stacks: " + result);
        helper.assertTrue(result.stream().noneMatch(stack -> stack.is(Items.DIAMOND_SWORD)), "gear in a chest: " + result);
        helper.assertTrue(result.stream().allMatch(stack -> pool.contains(stack.getItem())), "item outside the loot pool: " + result);
        // What the pack builds with is loot (andesite alloy, iron); a later machine's product (TFMG steel) and plain stone are not.
        var steel = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tfmg:steel_ingot"));
        var alloy = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:andesite_alloy"));
        helper.assertTrue((alloy == Items.AIR || pool.contains(alloy)) && pool.contains(Items.IRON_INGOT) && !pool.contains(Items.BONE) && !pool.contains(steel)
            && !pool.contains(Items.STONE), "loot table is off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strayLootIsCleanedFromContainers(GameTestHelper helper) {
        var level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
            .create(LootContextParamSets.CHEST);
        var container = new net.minecraft.world.SimpleContainer(3);
        container.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        container.setItem(1, new ItemStack(Items.RAW_IRON, 2));
        container.setItem(2, new ItemStack(Items.MUSIC_DISC_CAT));
        net.expedition.content.ChestLoot.sanitize(container, params);
        helper.assertTrue(container.getItem(0).isEmpty() && container.getItem(2).isEmpty(), "gear/disc left after the clean-up");
        helper.assertTrue(container.getItem(1).is(Items.RAW_IRON), "a loot-pool item was removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyVanillaVillagesArePeaceful(GameTestHelper helper) {
        helper.assertTrue(net.expedition.garrison.Garrisons.isPeacefulId("minecraft:village_plains"), "vanilla village has a garrison");
        helper.assertFalse(net.expedition.garrison.Garrisons.isPeacefulId("towns_and_towers:village_beach"), "modded village is peaceful");
        helper.assertFalse(net.expedition.garrison.Garrisons.isPeacefulId("create_sky_village:highwind_village"), "sky island is peaceful");
        helper.assertTrue(net.expedition.garrison.Garrisons.isPeacefulId("mss:tree_4"), "a tree got a garrison");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void structureValuablesBecomeFacades(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos gold = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos wool = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(gold, Blocks.GOLD_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(wool, Blocks.RED_WOOL.defaultBlockState());
        int swapped = StructureFacades.process(level.getChunkAt(gold));
        helper.assertTrue(swapped >= 2, "nothing swapped: " + swapped);
        var goldFacade = level.getBlockState(gold);
        var woolFacade = level.getBlockState(wool);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(goldFacade.getBlock()).getPath().equals("gold_facade")
            && BuiltInRegistries.BLOCK.getKey(woolFacade.getBlock()).getPath().equals("red_worn_canvas"), "wrong facades: " + goldFacade + " " + woolFacade);
        helper.assertTrue(Block.getDrops(goldFacade, level, gold, null).isEmpty() && Block.getDrops(woolFacade, level, wool, null).isEmpty(), "facades drop items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bossDropsHeartInsteadOfGear(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos.ZERO.above());
        LootParams params = new LootParams.Builder(helper.getLevel())
            .withParameter(LootContextParams.THIS_ENTITY, zombie)
            .withParameter(LootContextParams.ORIGIN, zombie.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic())
            .create(LootContextParamSets.ENTITY);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());
        BossLoot rule = new BossLoot(new LootItemCondition[0], List.of(new BossLoot.Boss(ResourceLocation.withDefaultNamespace("zombie"),
            List.of(ResourceLocation.withDefaultNamespace("rotten_flesh")))));
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(new ItemStack(Items.ROTTEN_FLESH, 2), new ItemStack(Items.BONE)));
        ObjectArrayList<ItemStack> result = rule.apply(loot, context);
        helper.assertTrue(result.stream().noneMatch(stack -> stack.is(Items.ROTTEN_FLESH)), "listed gear still dropped: " + result);
        helper.assertTrue(result.stream().anyMatch(stack -> stack.is(Items.BONE)), "unrelated drop was removed: " + result);
        ItemStack heart = result.stream().filter(stack -> stack.is(ExpeditionRegistry.BOSS_HEART.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!heart.isEmpty() && ResourceLocation.withDefaultNamespace("zombie").equals(heart.get(ExpeditionRegistry.BOSS.get())),
            "no heart of the zombie: " + result);
        helper.assertTrue(heart.getHoverName().getString().contains("Zombie"), "heart name doesn't name the boss: " + heart.getHoverName().getString());
        helper.succeed();
    }

    /** Late gun ammo: each tier is mixed from the one before, and the guns' data (ours, over Gunsmithing's) takes it. */
    @GameTest(template = "empty")
    public static void gunAmmoTiersAreCraftedAndFired(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (!net.neoforged.fml.ModList.get().isLoaded("cgs")) {
            helper.succeed();
            return;
        }
        for (String gun : new String[] {"revolver", "gatling", "shotgun"}) {
            for (String tier : new String[] {"black_steel", "ignitium", "cursium"}) {
                var id = ResourceLocation.fromNamespaceAndPath("expedition", "ammo/round_" + gun + "_" + tier);
                helper.assertTrue(server.getRecipeManager().byKey(id).isPresent(), "no recipe " + id);
            }
            var weapon = ResourceLocation.fromNamespaceAndPath("cgs", "weapons/" + gun + ".json");
            var top = server.getResourceManager().getResource(weapon).orElse(null);
            helper.assertTrue(top != null && top.sourcePackId().contains("expedition"), gun + ": Gunsmithing's own data wins over ours: " + (top == null ? null : top.sourcePackId()));
        }
        helper.succeed();
    }
}
