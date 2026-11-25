package net.expedition.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.expedition.world.realm.Realm;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Dungeon chest contents: a hand-made table ({@link #TABLE}) of the materials a player of this pack uses up, weighted
 * by how much the pack really eats them (/expedition audit ingredients: distinct recipes and mods an item goes into).
 * Create's andesite alloy above all, iron (its sheets go into 338 recipes: chests hold the ingots), copper, zinc, brass, fuel (coal), wood, wool,
 * glass, redstone; the realms' own materials (and wood) in their lands. Three rules:
 * <ul>
 * <li>Raw materials only: what is mined, grown, gathered or smelted (ores, ingots, nuggets, gems, logs, crops, wool,
 * string, glass, coal) - nothing made of them: no sheets, tubes, wires, mechanisms or other parts (a player presses and
 * assembles those), no machines, tools, ammunition, dishes or potions. Andesite alloy, Create's base material, is the
 * one exception.</li>
 * <li>What gear is crafted of lies in chests too: the pack's trophies, Apotheosis' materials and every mod material
 * its gear recipes take ({@link #gearMaterials}: fangs, runes, dungeon keys), rarer and further out the tougher the
 * mob that drops it. Only the story bosses' own drops stay with them.</li>
 * <li>Later industry's metals (TFMG steel, aluminium, lead; titanium) only from the second tier on, when it is built.</li>
 * </ul>
 * Each entry has a value (junk to epic: the dungeon's tier, by its difficulty and danger, shifts the odds towards valuable
 * ones), a weight within it, a stack size and the first tier it lies in.
 */
public final class DungeonLoot {
    private DungeonLoot() {
    }

    public static final int JUNK = 0, COMMON = 1, UNCOMMON = 2, RARE = 3, EPIC = 4;
    static final String[] VALUE_NAMES = {"junk", "common", "uncommon", "rare", "epic"};

    /**
     * How likely an item of each value is next to a common one of the same weight, per chest tier (tiers by the
     * place's difficulty, ChestLoot.tierDistance: chapters I-II, III-IV, V-VI, VII).
     */
    static final double[][] RARITY = {
        {0.6, 1, 0.8, 0.3, 0},
        {0.4, 1, 1.1, 0.55, 0.15},
        {0.3, 1, 1.4, 0.85, 0.35},
        {0.2, 1, 1.7, 1.2, 0.6}};
    static final double[] TIER_DISTANCE = {1400, 3200, 5200};

    /** One line of the table: what, how valuable, how likely within its value, how many, from which tier, where. */
    record Entry(String id, int value, int weight, int min, int max, int minTier, Realm home) {
    }

    private static Entry e(String id, int value, int weight, int min, int max) {
        return new Entry(id, value, weight, min, max, 0, null);
    }

    private static Entry e(String id, int value, int weight, int min, int max, int minTier) {
        return new Entry(id, value, weight, min, max, minTier, null);
    }

    private static Entry in(Realm realm, String id, int value, int weight, int min, int max) {
        return new Entry(id, value, weight, min, max, 0, realm);
    }

    static final List<Entry> TABLE = List.of(
        // Junk: a ruin's leftovers.
        e("minecraft:stick", JUNK, 4, 2, 8), e("minecraft:flint", JUNK, 3, 1, 4), e("minecraft:gravel", JUNK, 3, 4, 16),
        // Common - what every Create build eats by the stack: andesite alloy (217 recipes in 31 mods) above everything.
        e("create:andesite_alloy", COMMON, 100, 4, 16),
        e("minecraft:iron_ingot", COMMON, 26, 2, 8), e("minecraft:iron_nugget", COMMON, 8, 4, 12),
        e("minecraft:copper_ingot", COMMON, 14, 2, 8), e("create:copper_nugget", COMMON, 4, 4, 12),
        e("create:zinc_ingot", COMMON, 14, 2, 6), e("create:zinc_nugget", COMMON, 6, 4, 12),
        e("minecraft:raw_iron", COMMON, 6, 2, 6), e("minecraft:raw_copper", COMMON, 5, 2, 8), e("create:raw_zinc", COMMON, 5, 2, 6),
        // Redstone and quartz: what tubes, links and comparators are made of.
        e("minecraft:redstone", COMMON, 18, 4, 12), e("minecraft:quartz", COMMON, 12, 2, 8),
        e("minecraft:andesite", COMMON, 8, 8, 24),
        // Fuel: burners, furnaces, engines.
        e("minecraft:coal", COMMON, 55, 4, 16), e("minecraft:charcoal", COMMON, 8, 4, 12), e("tfmg:coal_coke", COMMON, 5, 2, 8, 1),
        // Wood, wool, glass, sand, clay, rubber: building by the stack.
        e("minecraft:oak_log", COMMON, 7, 4, 16), e("minecraft:spruce_log", COMMON, 7, 4, 16), e("minecraft:birch_log", COMMON, 5, 4, 16),
        e("minecraft:dark_oak_log", COMMON, 4, 4, 16), e("minecraft:jungle_log", COMMON, 3, 4, 12), e("minecraft:acacia_log", COMMON, 3, 4, 12),
        e("minecraft:white_wool", COMMON, 10, 2, 8), e("minecraft:glass", COMMON, 10, 4, 16),
        e("minecraft:sand", COMMON, 6, 8, 16), e("minecraft:clay_ball", COMMON, 5, 4, 12), e("minecraft:sugar_cane", COMMON, 5, 2, 8),
        e("rubberworks:rubber", COMMON, 6, 2, 6),
        // Everyday materials mobs drop too, by use: string (25 mods), leather and slime (13), gunpowder for cannons (12).
        e("minecraft:string", COMMON, 9, 2, 8), e("minecraft:gunpowder", COMMON, 7, 2, 8), e("minecraft:leather", COMMON, 6, 1, 4),
        e("minecraft:feather", COMMON, 3, 1, 4),
        // A farm's start: crops, seeds, plants.
        e("minecraft:wheat", COMMON, 4, 4, 12), e("minecraft:wheat_seeds", COMMON, 3, 2, 8), e("minecraft:carrot", COMMON, 3, 2, 6),
        e("minecraft:potato", COMMON, 3, 2, 6), e("farmersdelight:onion", COMMON, 2, 1, 4), e("farmersdelight:rice", COMMON, 2, 1, 4),
        e("farmersdelight:tomato_seeds", COMMON, 2, 1, 4), e("farmersdelight:cabbage_seeds", COMMON, 2, 1, 4), e("farmersdelight:straw", COMMON, 3, 2, 8),
        e("minecraft:oak_sapling", COMMON, 2, 1, 4), e("minecraft:bamboo", COMMON, 3, 4, 12), e("minecraft:kelp", COMMON, 2, 4, 12),
        e("minecraft:cactus", COMMON, 2, 2, 6), e("minecraft:pumpkin_seeds", COMMON, 2, 2, 6), e("minecraft:melon_seeds", COMMON, 2, 2, 6),
        e("minecraft:sweet_berries", COMMON, 2, 2, 6), e("minecraft:cocoa_beans", COMMON, 2, 2, 6), e("minecraft:brown_mushroom", COMMON, 2, 1, 4),
        e("minecraft:red_mushroom", COMMON, 2, 1, 4), e("minecraft:moss_block", COMMON, 2, 2, 6), e("minecraft:poppy", COMMON, 2, 1, 4),
        e("minecraft:dandelion", COMMON, 2, 1, 4), e("minecraft:cornflower", COMMON, 2, 1, 4),
        // Uncommon: brass, gold, gems, cast iron.
        e("create:brass_ingot", UNCOMMON, 16, 1, 4), e("create:brass_nugget", UNCOMMON, 6, 3, 9),
        e("minecraft:gold_ingot", UNCOMMON, 8, 1, 3), e("minecraft:gold_nugget", UNCOMMON, 6, 3, 9),
        e("minecraft:lapis_lazuli", UNCOMMON, 6, 2, 8), e("minecraft:amethyst_shard", UNCOMMON, 4, 1, 4),
        e("createbigcannons:cast_iron_ingot", UNCOMMON, 4, 1, 4), e("createbigcannons:cast_iron_nugget", COMMON, 3, 3, 9),
        e("minecraft:obsidian", UNCOMMON, 4, 2, 6), e("minecraft:slime_ball", UNCOMMON, 5, 1, 4), e("minecraft:honeycomb", UNCOMMON, 3, 1, 4),
        e("createdeco:industrial_iron_ingot", UNCOMMON, 3, 1, 4),
        // TFMG, the heavy industry (later chapters): its ores and metals.
        e("tfmg:raw_lead", COMMON, 3, 2, 6, 1), e("tfmg:raw_nickel", COMMON, 2, 2, 6, 1), e("tfmg:galena", COMMON, 2, 2, 6, 1),
        e("tfmg:sulfur_dust", UNCOMMON, 3, 1, 4, 1), e("tfmg:steel_ingot", UNCOMMON, 5, 1, 3, 1), e("tfmg:steel_nugget", UNCOMMON, 3, 3, 9, 1),
        e("tfmg:aluminum_ingot", UNCOMMON, 4, 1, 4, 1), e("tfmg:lead_ingot", UNCOMMON, 3, 1, 4, 1), e("tfmg:nickel_ingot", UNCOMMON, 2, 1, 3, 1),
        e("tfmg:cast_iron_ingot", UNCOMMON, 2, 1, 4, 1),
        // Aircraft metal, platinum, Galosphere's cave metals and crystals.
        e("create_the_air_wars:titaniumnugget", UNCOMMON, 3, 3, 9, 1), e("create_the_air_wars:titaniumingot", UNCOMMON, 3, 1, 3, 1),
        e("createpropulsion:raw_platinum", UNCOMMON, 3, 1, 4, 1),
        e("galosphere:palladium_ingot", UNCOMMON, 3, 1, 3), e("galosphere:allurite_shard", UNCOMMON, 3, 1, 4), e("galosphere:pink_salt_shard", COMMON, 2, 2, 6),
        // Apotheosis' materials (gems, artifacts, relics and Aether gear are crafted with them): found, not brewed.
        // Weighted by use (loot_needs.tsv): the shard goes into 57 recipes, the fabric 42, the sands 40, the dust 23.
        e("apotheosis:timeworn_fabric", UNCOMMON, 16, 1, 4), e("apotheosis:gem_dust", UNCOMMON, 10, 2, 6, 1),
        e("apotheosis:luminous_crystal_shard", RARE, 14, 1, 4, 1), e("apotheosis:arcane_sands", RARE, 10, 1, 3, 2),
        // The pack's trophies (gear and artifacts are crafted with them): in chests, no longer left to a mob's luck.
        e("expedition:feral_fang", COMMON, 14, 1, 4), e("expedition:grave_dust", COMMON, 8, 1, 4), e("expedition:chitin_plate", COMMON, 6, 1, 4),
        e("expedition:raider_insignia", COMMON, 6, 1, 4), e("expedition:abyssal_scale", COMMON, 6, 1, 4),
        e("expedition:cinder_core", UNCOMMON, 4, 1, 3, 1), e("expedition:void_shard", RARE, 4, 1, 3, 2),
        // Vanilla monsters' parts recipes take, no longer only theirs (blaze rods: 7 recipes, 4 each).
        e("minecraft:prismarine_crystals", UNCOMMON, 3, 1, 4), e("minecraft:rabbit_foot", UNCOMMON, 2, 1, 2), e("minecraft:turtle_scute", RARE, 2, 1, 2),
        e("minecraft:goat_horn", RARE, 1, 1, 1), e("minecraft:heart_of_the_sea", EPIC, 1, 1, 1, 1), e("minecraft:totem_of_undying", EPIC, 1, 1, 1, 1),
        // Rare: the classic rewards and the costlier finds, further out.
        e("aeronautics:levitite", RARE, 3, 1, 3, 1), e("minecraft:enchanted_book", RARE, 3, 1, 1), e("minecraft:experience_bottle", RARE, 3, 2, 6),
        e("minecraft:name_tag", RARE, 1, 1, 1), e("minecraft:saddle", RARE, 1, 1, 1), e("minecraft:ender_pearl", RARE, 2, 1, 2),
        e("minecraft:emerald", RARE, 3, 1, 3), e("minecraft:raw_gold", RARE, 4, 2, 5),
        // Epic.
        e("minecraft:diamond", EPIC, 8, 1, 2, 1), e("minecraft:netherite_scrap", EPIC, 3, 1, 1, 2),
        // The Nether's own: quartz, glowstone, wart, its wood and stone.
        in(Realm.NETHER, "minecraft:quartz", COMMON, 10, 2, 8), in(Realm.NETHER, "minecraft:glowstone_dust", UNCOMMON, 6, 2, 6),
        in(Realm.NETHER, "minecraft:nether_wart", UNCOMMON, 5, 2, 6), in(Realm.NETHER, "minecraft:blaze_powder", UNCOMMON, 4, 1, 4),
        in(Realm.NETHER, "minecraft:crimson_stem", COMMON, 5, 4, 12), in(Realm.NETHER, "minecraft:warped_stem", COMMON, 5, 4, 12),
        in(Realm.NETHER, "minecraft:blackstone", COMMON, 3, 4, 16), in(Realm.NETHER, "minecraft:soul_sand", COMMON, 3, 4, 12),
        in(Realm.NETHER, "minecraft:basalt", COMMON, 2, 4, 12), in(Realm.NETHER, "minecraft:magma_block", COMMON, 2, 2, 6),
        in(Realm.NETHER, "expedition:cinder_core", COMMON, 14, 1, 4), in(Realm.NETHER, "minecraft:blaze_rod", UNCOMMON, 8, 1, 4),
        in(Realm.NETHER, "minecraft:ghast_tear", RARE, 2, 1, 2), in(Realm.NETHER, "minecraft:wither_rose", RARE, 1, 1, 1),
        // The End's.
        in(Realm.END, "minecraft:chorus_fruit", COMMON, 5, 2, 6), in(Realm.END, "aeronautics:end_stone_powder", COMMON, 6, 2, 6),
        in(Realm.END, "aeronautics:levitite", UNCOMMON, 4, 1, 3), in(Realm.END, "minecraft:end_stone", COMMON, 3, 8, 16),
        in(Realm.END, "expedition:void_shard", COMMON, 14, 1, 4),
        // The Aether's: its ores, amber, skyroot, holystone.
        in(Realm.AETHER, "aether:ambrosium_shard", COMMON, 10, 2, 8), in(Realm.AETHER, "aether:golden_amber", COMMON, 5, 1, 4),
        in(Realm.AETHER, "aether:skyroot_log", COMMON, 8, 4, 16), in(Realm.AETHER, "aether:holystone", COMMON, 4, 8, 16),
        in(Realm.AETHER, "aether:zanite_gemstone", UNCOMMON, 8, 1, 4), in(Realm.AETHER, "deep_aether:skyjade", UNCOMMON, 6, 1, 3),
        in(Realm.AETHER, "aether:enchanted_gravitite", RARE, 2, 1, 2),
        // Its dungeon keys (Neptune, Valkyrie and Phoenix gear are made with them), by the dungeon they open.
        in(Realm.AETHER, "aether:bronze_dungeon_key", RARE, 8, 1, 1), new Entry("aether:silver_dungeon_key", RARE, 10, 1, 1, 1, Realm.AETHER),
        new Entry("deep_aether:brass_dungeon_key", RARE, 3, 1, 1, 1, Realm.AETHER), new Entry("aether:gold_dungeon_key", EPIC, 6, 1, 1, 2, Realm.AETHER),
        // The Twilight Forest's: its plants, ingots and wood.
        in(Realm.TWILIGHT, "twilightforest:torchberries", COMMON, 4, 2, 6), in(Realm.TWILIGHT, "twilightforest:liveroot", COMMON, 5, 1, 4),
        in(Realm.TWILIGHT, "twilightforest:twilight_oak_log", COMMON, 6, 4, 16), in(Realm.TWILIGHT, "twilightforest:canopy_log", COMMON, 4, 4, 12),
        in(Realm.TWILIGHT, "twilightforest:ironwood_ingot", UNCOMMON, 6, 1, 3), in(Realm.TWILIGHT, "twilightforest:steeleaf_ingot", UNCOMMON, 5, 1, 3),
        in(Realm.TWILIGHT, "twilightforest:knightmetal_ingot", RARE, 3, 1, 2),
        // The Otherside's (its other materials come from its creatures).
        in(Realm.OTHERSIDE, "deeperdarker:echo_log", COMMON, 6, 4, 16), in(Realm.OTHERSIDE, "deeperdarker:gloomslate", COMMON, 3, 8, 16),
        in(Realm.OTHERSIDE, "create_deep_dark:echo_ingot", UNCOMMON, 3, 1, 3));

    private static volatile Map<Item, Entry> entries = Map.of();
    /** Per value: items of the table that exist in this pack (an entry of an absent mod is left out). */
    @SuppressWarnings("unchecked")
    private static volatile List<Item>[] pool = new List[] {List.of(), List.of(), List.of(), List.of(), List.of()};
    /** Per item used in a recipe: {non-food recipes using it, distinct non-food results, mods of those results} (audit). */
    private static volatile Map<Item, int[]> usage = Map.of();

    public static List<Item> pool(int value) {
        return pool[value];
    }

    public static int[] usage(Item item) {
        return usage.getOrDefault(item, new int[3]);
    }

    /** An item's weight within its value (0: not loot). */
    public static int weight(Item item) {
        Entry entry = entries.get(item);
        return entry == null ? 0 : entry.weight();
    }

    /** Recipe ingredients that aren't loot (for /expedition audit loot: what the table leaves out, by use). */
    public static Map<Item, String> rejected() {
        Map<Item, String> out = new java.util.HashMap<>();
        usage.keySet().forEach(item -> {
            if (!entries.containsKey(item)) {
                out.put(item, "not in the loot table");
            }
        });
        return out;
    }

    /** Resolves the table (after every recipe reload) and counts recipe use for the audit. */
    @SuppressWarnings("unchecked")
    static void rebuild(RecipeManager manager, HolderLookup.Provider registries, Set<Item> made, Map<Item, Double> dropperHealth) {
        Map<Item, Integer> uses = new java.util.HashMap<>();
        Map<Item, Set<Item>> results = new java.util.HashMap<>();
        Map<Item, Set<String>> resultMods = new java.util.HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ItemStack result = holder.value().getResultItem(registries);
            if (result.has(DataComponents.FOOD)) {
                continue;
            }
            Set<Item> inThisRecipe = new HashSet<>();
            for (Ingredient ingredient : holder.value().getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    inThisRecipe.add(stack.getItem());
                }
            }
            inThisRecipe.forEach(item -> {
                uses.merge(item, 1, Integer::sum);
                results.computeIfAbsent(item, k -> new HashSet<>()).add(result.getItem());
                resultMods.computeIfAbsent(item, k -> new HashSet<>()).add(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace());
            });
        }
        Map<Item, int[]> nextUsage = new java.util.HashMap<>();
        uses.forEach((item, count) -> nextUsage.put(item, new int[] {count, results.get(item).size(), resultMods.get(item).size()}));
        usage = nextUsage;
        // An item may have two lines (ender pearls: rare anywhere, uncommon in the End); each line is drawn on its own.
        Map<Item, Entry> next = new java.util.LinkedHashMap<>();
        List<Item>[] nextPool = new List[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        List<Entry> resolved = new ArrayList<>();
        List<Entry> table = new ArrayList<>(TABLE);
        List<Entry> materials = gearMaterials(manager, made, dropperHealth);
        table.addAll(materials);
        net.expedition.Expedition.LOG.info("Dungeon loot: {} mod materials of the pack's gear recipes: {}", materials.size(),
            materials.stream().map(entry -> entry.id() + "=" + VALUE_NAMES[entry.value()] + "/t" + entry.minTier()).toList());
        for (Entry entry : table) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
            if (item == Items.AIR) {
                net.expedition.Expedition.LOG.warn("Dungeon loot: no item {} in this pack", entry.id());
                continue;
            }
            resolved.add(entry);
            next.putIfAbsent(item, entry);
            if (!nextPool[entry.value()].contains(item)) {
                nextPool[entry.value()].add(item);
            }
        }
        lines = List.copyOf(resolved);
        entries = next;
        pool = nextPool;
        net.expedition.Expedition.LOG.info("Dungeon loot table: {} entries ({} junk, {} common, {} uncommon, {} rare, {} epic)", resolved.size(),
            nextPool[0].size(), nextPool[1].size(), nextPool[2].size(), nextPool[3].size(), nextPool[4].size());
    }

    /** Mods whose lands are a realm of the merged world: their materials lie in that realm's chests only. */
    private static final Map<String, Realm> MOD_REALMS = Map.of("aether", Realm.AETHER, "deep_aether", Realm.AETHER,
        "twilightforest", Realm.TWILIGHT, "deeperdarker", Realm.OTHERSIDE, "create_deep_dark", Realm.OTHERSIDE);

    /**
     * The mods' materials the pack's own recipes (its gear, artifacts and ammunition: the expedition namespace) are made
     * of and no recipe makes (a mob's fang, a dungeon key, a rune): chest loot, so gear doesn't wait on a lucky drop. How
     * rare and how far out goes by the weakest mob that drops it (a common beast's: uncommon from the start; a boss's:
     * epic, far out); never the story bosses' own drops (their hearts and loot come from them).
     */
    static List<Entry> gearMaterials(RecipeManager manager, Set<Item> made, Map<Item, Double> dropperHealth) {
        Set<Item> bossOwn = new HashSet<>();
        for (var entry : net.expedition.route.RouteSteps.all()) {
            entry.step().heart().forEach(id -> bossOwn.add(BuiltInRegistries.ITEM.get(id)));
            entry.step().loot().forEach(id -> bossOwn.add(BuiltInRegistries.ITEM.get(id)));
        }
        Set<Item> listed = new HashSet<>();
        TABLE.forEach(entry -> listed.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()))));
        // Per material: recipes taking it, and the most one recipe takes.
        Map<Item, int[]> use = new java.util.LinkedHashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!holder.id().getNamespace().equals(net.expedition.Expedition.MOD_ID)) {
                continue;
            }
            Map<Item, Integer> inThis = new java.util.HashMap<>();
            for (Ingredient ingredient : holder.value().getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (options.length != 1) {
                    continue; // a tag (any plank, any wool) is an everyday thing, not a special find
                }
                Item item = options[0].getItem();
                String mod = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                if (mod.equals("minecraft") || mod.equals("create") || mod.equals(net.expedition.Expedition.MOD_ID) || made.contains(item)
                    || bossOwn.contains(item) || listed.contains(item) || ChestLoot.isGear(options[0])) {
                    continue;
                }
                inThis.merge(item, 1, Integer::sum);
            }
            inThis.forEach((item, count) -> {
                int[] u = use.computeIfAbsent(item, k -> new int[2]);
                u[0]++;
                u[1] = Math.max(u[1], count);
            });
        }
        List<Entry> found = new ArrayList<>();
        use.forEach((item, u) -> {
            Double health = dropperHealth.get(item);
            int value = health == null ? UNCOMMON : health <= 40 ? UNCOMMON : health <= 300 ? RARE : EPIC;
            int minTier = health == null ? 1 : health <= 40 ? 0 : health <= 100 ? 1 : 2;
            // Likelier the more recipes take it; a stack (halved when drawn) about one recipe's worth.
            found.add(new Entry(BuiltInRegistries.ITEM.getKey(item).toString(), value, Math.min(8, 2 + u[0]), 1, Math.max(2, u[1]), minTier,
                MOD_REALMS.get(BuiltInRegistries.ITEM.getKey(item).getNamespace())));
        });
        return found;
    }

    /** Mods a dungeon may lean to (its theme): their lines are this much likelier in all its chests. */
    static final int THEMES = 2, THEME_BOOST = 4;

    /** A dungeon's theme: {@link #THEMES} mods of the table (besides Minecraft and Create, which are everywhere), by its key. */
    static Set<String> theme(String dungeonKey) {
        if (dungeonKey.isEmpty()) {
            return Set.of();
        }
        List<String> mods = new ArrayList<>(lines.stream().filter(entry -> entry.home() == null).map(entry -> ResourceLocation.parse(entry.id()).getNamespace())
            .filter(mod -> !mod.equals("minecraft") && !mod.equals("create")).distinct().sorted().toList());
        java.util.Collections.shuffle(mods, new java.util.Random(dungeonKey.hashCode()));
        return Set.copyOf(mods.subList(0, Math.min(THEMES, mods.size())));
    }

    /** The table's lines whose item exists in this pack. */
    private static volatile List<Entry> lines = List.of();

    public static int tierAt(double distance) {
        int tier = 0;
        while (tier < TIER_DISTANCE.length && distance >= TIER_DISTANCE[tier]) {
            tier++;
        }
        return tier;
    }

    public static List<ItemStack> generate(RandomSource random, double distance, int rolls) {
        return generate(random, distance, rolls, EPIC);
    }

    public static List<ItemStack> generate(RandomSource random, double distance, int rolls, int maxValue) {
        return generate(random, distance, rolls, maxValue, Realm.OVERWORLD, 0, "", Set.of());
    }

    /** Danger per chest -> tiers above the distance's: a hard dungeon pays like a farther one (up to 2 tiers). */
    public static int tierBonus(double danger) {
        return danger >= 30 ? 2 : danger >= 12 ? 1 : 0;
    }

    /**
     * Stacks for one chest: {@code rolls} weighted draws over the table's lines that fit (value at most {@code maxValue},
     * the tier reached, the chest's realm), one stack per item; the lines of the dungeon's theme mods ({@link #theme}) and
     * its own trophies ({@code favored}) are likelier, so two dungeons' chests differ.
     */
    public static List<ItemStack> generate(RandomSource random, double distance, int rolls, int maxValue, Realm realm, int tierBonus, String dungeonKey,
                                           Set<Item> favored) {
        int tier = Math.min(RARITY.length - 1, tierAt(distance) + tierBonus);
        Set<String> theme = theme(dungeonKey);
        List<ItemStack> result = new ArrayList<>();
        Set<Item> used = new HashSet<>();
        for (int i = 0; i < rolls; i++) {
            List<Entry> candidates = new ArrayList<>();
            List<Double> chances = new ArrayList<>();
            for (Entry entry : lines) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
                if (entry.value() > maxValue || used.contains(item) || tier < entry.minTier() || entry.home() != null && entry.home() != realm
                    || RARITY[tier][entry.value()] == 0) {
                    continue;
                }
                candidates.add(entry);
                chances.add(entry.weight() * RARITY[tier][entry.value()] * (theme.contains(ResourceLocation.parse(entry.id()).getNamespace()) ? THEME_BOOST : 1)
                    * (favored.contains(item) ? THEME_BOOST : 1));
            }
            if (candidates.isEmpty()) {
                break;
            }
            Entry entry = candidates.get(pickWeighted(random, chances));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
            used.add(item);
            // Half the table's stack: a dungeon's chests were a whole game's supply of ingots.
            int amount = (entry.min() + random.nextInt(entry.max() - entry.min() + 1) + 1) / 2;
            result.add(new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), amount)));
        }
        return result;
    }

    private static int pickWeighted(RandomSource random, List<Double> chances) {
        double total = 0;
        for (double chance : chances) {
            total += chance;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < chances.size(); i++) {
            roll -= chances.get(i);
            if (roll < 0) {
                return i;
            }
        }
        return chances.size() - 1;
    }
}
