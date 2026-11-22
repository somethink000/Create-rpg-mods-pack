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
 * <li>No special mob drops: a mod's monster and boss materials (fangs, runes, boss scales, the stuff its weapons are
 * made of) come from fighting them. Everyday materials that mobs drop too (string, leather, gunpowder, slime) are
 * ingredients like any other and lie in chests by how much they are used.</li>
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
        // Apotheosis salvage: what gear is reforged with.
        e("apotheosis:mysterious_scrap_metal", UNCOMMON, 3, 1, 3), e("apotheosis:timeworn_fabric", UNCOMMON, 3, 1, 3, 1),
        e("apotheosis:luminous_crystal_shard", RARE, 2, 1, 2, 1),
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
        // The End's.
        in(Realm.END, "minecraft:chorus_fruit", COMMON, 5, 2, 6), in(Realm.END, "aeronautics:end_stone_powder", COMMON, 6, 2, 6),
        in(Realm.END, "aeronautics:levitite", UNCOMMON, 4, 1, 3), in(Realm.END, "minecraft:end_stone", COMMON, 3, 8, 16),
        // The Aether's: its ores, amber, skyroot, holystone.
        in(Realm.AETHER, "aether:ambrosium_shard", COMMON, 10, 2, 8), in(Realm.AETHER, "aether:golden_amber", COMMON, 5, 1, 4),
        in(Realm.AETHER, "aether:skyroot_log", COMMON, 8, 4, 16), in(Realm.AETHER, "aether:holystone", COMMON, 4, 8, 16),
        in(Realm.AETHER, "aether:zanite_gemstone", UNCOMMON, 8, 1, 4), in(Realm.AETHER, "deep_aether:skyjade", UNCOMMON, 6, 1, 3),
        in(Realm.AETHER, "aether:enchanted_gravitite", RARE, 2, 1, 2),
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
    static void rebuild(RecipeManager manager, HolderLookup.Provider registries) {
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
        for (Entry entry : TABLE) {
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
        return generate(random, distance, rolls, maxValue, Realm.OVERWORLD, 0, "");
    }

    /** Danger per chest -> tiers above the distance's: a hard dungeon pays like a farther one (up to 2 tiers). */
    public static int tierBonus(double danger) {
        return danger >= 30 ? 2 : danger >= 12 ? 1 : 0;
    }

    /**
     * Stacks for one chest: {@code rolls} weighted draws over the table's lines that fit (value at most {@code maxValue},
     * the tier reached, the chest's realm), one stack per item; the lines of the dungeon's theme mods ({@link #theme}) are
     * likelier, so two dungeons' chests differ.
     */
    public static List<ItemStack> generate(RandomSource random, double distance, int rolls, int maxValue, Realm realm, int tierBonus, String dungeonKey) {
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
                chances.add(entry.weight() * RARITY[tier][entry.value()] * (theme.contains(ResourceLocation.parse(entry.id()).getNamespace()) ? THEME_BOOST : 1));
            }
            if (candidates.isEmpty()) {
                break;
            }
            Entry entry = candidates.get(pickWeighted(random, chances));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
            used.add(item);
            int amount = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
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
