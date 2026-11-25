package net.expedition.content;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.expedition.garrison.Garrisons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Dungeon chests: their contents are generated (see {@link DungeonLoot}): crafting materials by the dungeon's tier,
 * some junk among them. Gear never lies in chests; special mod items (uncraftable) a mod's own chest table puts in
 * stay only in a dungeon of their own mod; anywhere else they are dropped.
 */
public final class ChestLoot extends LootModifier {
    public static final MapCodec<ChestLoot> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, ChestLoot::new));

    static final int MIN_ROLLS = 3;
    static final int MAX_ROLLS = 8;
    /** Outside dungeons (villages, peaceful buildings): a few everyday stacks, nothing a fight should pay for. */
    static final int PEACEFUL_ROLLS = 3;
    /** One more stack per this much danger per chest (Garrisons.dangerPerChest), at most this many more. */
    static final double DANGER_PER_ROLL = 6;
    static final int MAX_DANGER_ROLLS = 7;
    /** Overworld blocks from spawn; the Nether uses 1/8 of it. */
    static final double DIAMOND_DISTANCE = 2200;
    static final double NETHERITE_DISTANCE = 4200;

    private static final List<String> GEAR_MODS = List.of("artifacts", "relics");

    public ChestLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    // ------------------------------------------------------------------ recipe index (filled after every recipe load)

    private static volatile Set<Item> craftable = Set.of();

    /** Items some recipe makes, and what each smelting result is smelted from (see RemovedItems). */
    public static void setRecipeIndex(Set<Item> made, Map<Item, Item> smelted) {
        craftable = Set.copyOf(made);
        mobDrops = null;
    }

    private static volatile Set<Item> mobDrops;
    private static volatile Map<Item, Double> dropperHealth = Map.of();

    /** Per mod item a mob's loot table names: the health of the weakest mob that drops it (DungeonLoot.gearMaterials). */
    public static Map<Item, Double> dropperHealth(net.minecraft.server.MinecraftServer server) {
        scanMobDrops(server);
        return dropperHealth;
    }

    /**
     * Mod items a mob's loot table names (fangs, fins, scales, boss metals): they come from fighting that mob, never from
     * a chest or another dungeon's garrison, even where a mod's own chest table lists them.
     */
    public static boolean isMobDrop(ServerLevel level, ItemStack stack) {
        scanMobDrops(level.getServer());
        return mobDrops.contains(stack.getItem());
    }

    private static void scanMobDrops(net.minecraft.server.MinecraftServer server) {
        if (mobDrops != null) {
            return;
        }
        Map<Item, Double> health = new java.util.HashMap<>();
        java.util.regex.Pattern name = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
        server.getResourceManager().listResources("loot_table/entities", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            // loot_table/entities/<mob>.json (or <mob>/<variant>.json) belongs to the entity <namespace>:<mob>.
            String mob = id.getPath().substring("loot_table/entities/".length()).replace(".json", "");
            double mobHealth = Garrisons.baseHealth(id.getNamespace() + ":" + (mob.contains("/") ? mob.substring(0, mob.indexOf('/')) : mob));
            try (var reader = resource.openAsReader()) {
                var matcher = name.matcher(reader.lines().collect(java.util.stream.Collectors.joining("\n")));
                while (matcher.find()) {
                    ResourceLocation item = ResourceLocation.tryParse(matcher.group(1));
                    if (item != null && !item.getNamespace().equals("minecraft")) {
                        BuiltInRegistries.ITEM.getOptional(item).ifPresent(found -> health.merge(found, mobHealth, Math::min));
                    }
                }
            } catch (java.io.IOException ignored) {
            }
        });
        dropperHealth = Map.copyOf(health);
        mobDrops = Set.copyOf(health.keySet());
    }

    // ------------------------------------------------------------------ classification

    /**
     * Chest-like loot: a position, but no killed entity, broken block or tool, and opened by a player or by nobody.
     * Equipment rolls (Artifacts' on every natural spawn, trial spawners) carry the mob itself and are not chests.
     */
    static boolean isChest(LootContext context) {
        return context.hasParam(LootContextParams.ORIGIN) && !context.hasParam(LootContextParams.DAMAGE_SOURCE)
            && !context.hasParam(LootContextParams.BLOCK_STATE) && !context.hasParam(LootContextParams.TOOL)
            && (!context.hasParam(LootContextParams.THIS_ENTITY) || context.getParam(LootContextParams.THIS_ENTITY) instanceof net.minecraft.world.entity.player.Player);
    }

    static boolean isGear(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof TieredItem || item instanceof ArmorItem || item instanceof ShieldItem || item instanceof ProjectileWeaponItem
            || item instanceof TridentItem || GEAR_MODS.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace());
    }

    public static boolean isGearPublic(ItemStack stack) {
        return isGear(stack);
    }

    static boolean isNetherite(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("netherite") || stack.is(Items.ANCIENT_DEBRIS);
    }

    static boolean isDiamond(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("diamond");
    }

    /** A mod item nobody can craft: dungeon loot proper, so the garrison carries it. */
    static boolean isSpecial(ItemStack stack) {
        return !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft") && !craftable.contains(stack.getItem());
    }

    /**
     * How hard the place is, on the loot tiers' scale (the route's old rings, in blocks: chapter I to 900, II to 1400...,
     * VII to 6500): never how far it lies. A story dungeon is its chapter's; any other dungeon is as hard as its garrison
     * (its mobs' health); outside dungeons the lowest. Chests, the garrison's parts and enchantments all read it.
     */
    public static double tierDistance(ServerLevel level, Vec3 origin) {
        BlockPos pos = BlockPos.containing(origin);
        String key = Garrisons.dungeonKeyAt(level, pos);
        if (key.isEmpty()) {
            return 0;
        }
        int act = net.expedition.route.Route.actOfDungeon(level.getServer(), key);
        if (act > 0) {
            return CHAPTER_MIDDLE[Math.min(act, CHAPTER_MIDDLE.length) - 1];
        }
        return CHAPTER_MIDDLE[chapterOfHealth(Garrisons.garrisonHealth(level, pos)) - 1];
    }

    /** The middle of each chapter's tier band on the loot scale. */
    private static final double[] CHAPTER_MIDDLE = {450, 1150, 1800, 2700, 3700, 4700, 5850};
    /** A garrison's mean health (a zombie is 20) up to which a dungeon is as hard as chapter I, II... (above the last: VII). */
    private static final double[] HEALTH_BANDS = {22, 30, 40, 52, 66, 85};

    public static int chapterOfHealth(double health) {
        int chapter = 1;
        while (chapter <= HEALTH_BANDS.length && health > HEALTH_BANDS[chapter - 1]) {
            chapter++;
        }
        return chapter;
    }

    /** A chest's stacks: dungeon loot by the place's tier inside a dungeon, everyday goods anywhere else. */
    public static List<ItemStack> generate(ServerLevel level, Vec3 origin, net.minecraft.util.RandomSource random, int rolls) {
        boolean dungeon = Garrisons.inDungeon(level, BlockPos.containing(origin));
        // A chest pays for the fight it stands for: more stacks and a higher tier where many tough mobs guard few chests.
        double danger = dungeon ? Garrisons.dangerPerChest(level, BlockPos.containing(origin)) : 0;
        int extra = (int) Math.min(MAX_DANGER_ROLLS, danger / DANGER_PER_ROLL);
        double distance = tierDistance(level, origin);
        String key = dungeon ? Garrisons.dungeonKeyAt(level, BlockPos.containing(origin)) : "";
        List<ItemStack> stacks = DungeonLoot.generate(random, distance, dungeon ? rolls + extra : Math.min(rolls, PEACEFUL_ROLLS),
            dungeon ? DungeonLoot.EPIC : DungeonLoot.COMMON, realmAt(level, origin), DungeonLoot.tierBonus(danger), key,
            key.isEmpty() ? Set.of() : Set.copyOf(net.expedition.route.Route.trophiesFor(level.getServer(), key)));
        // An enchanted book line: a real book, enchanted as at a table of the place's tier (harder: stronger).
        int enchantLevel = 8 + 7 * DungeonLoot.tierAt(distance);
        stacks.replaceAll(stack -> stack.is(Items.ENCHANTED_BOOK) && !stack.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)
            ? net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), enchantLevel, level.registryAccess(),
                java.util.Optional.empty())
            : stack);
        return stacks;
    }

    /** The realm a chest stands in: the Nether and End dimensions, or the realm of the merged world at its place. */
    static net.expedition.world.realm.Realm realmAt(ServerLevel level, Vec3 origin) {
        if (level.dimension() == Level.NETHER) {
            return net.expedition.world.realm.Realm.NETHER;
        }
        if (level.dimension() == Level.END) {
            return net.expedition.world.realm.Realm.END;
        }
        return level.dimension() == Level.OVERWORLD ? net.expedition.world.realm.RealmLayout.dominant((int) origin.x, (int) origin.z)
            : net.expedition.world.realm.Realm.OVERWORLD;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!isChest(context)) {
            return loot;
        }
        ServerLevel level = context.getLevel();
        Vec3 origin = context.getParam(LootContextParams.ORIGIN);
        List<ItemStack> special = loot.stream().filter(stack -> !isGear(stack) && isSpecial(stack) && !isMobDrop(level, stack)
            && !stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)).toList();
        int rolls = Math.max(MIN_ROLLS, Math.min(MAX_ROLLS, (int) loot.stream().filter(stack -> !stack.isEmpty()).count()));
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>(generate(level, origin, context.getRandom(), rolls));
        if (!special.isEmpty()) {
            result.addAll(Garrisons.handleSpecialLoot(level, BlockPos.containing(origin), special));
        }
        return result;
    }

    /**
     * Final pass over a filled container: loot modifiers of other mods may run after ours and add artifacts, discs or
     * smithing templates. Only loot-pool items stay; special items go through the same rule as above.
     */
    public static void sanitize(net.minecraft.world.Container container, net.minecraft.world.level.storage.loot.LootParams params) {
        if (!params.hasParam(LootContextParams.ORIGIN) || params.hasParam(LootContextParams.DAMAGE_SOURCE)) {
            return;
        }
        java.util.Set<Item> allowed = new java.util.HashSet<>();
        for (int value = 0; value <= DungeonLoot.EPIC; value++) {
            allowed.addAll(DungeonLoot.pool(value));
        }
        List<ItemStack> strays = new ArrayList<>();
        java.util.List<Integer> straySlots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && !allowed.contains(stack.getItem())) {
                strays.add(stack);
                straySlots.add(slot);
            }
        }
        if (strays.isEmpty()) {
            return;
        }
        List<ItemStack> special = strays.stream().filter(stack -> !isGear(stack) && isSpecial(stack) && !isMobDrop(params.getLevel(), stack)
            && !stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)).toList();
        List<ItemStack> keep = special.isEmpty() ? List.of()
            : Garrisons.handleSpecialLoot(params.getLevel(), BlockPos.containing(params.getParameter(LootContextParams.ORIGIN)), special);
        for (int i = 0; i < strays.size(); i++) {
            if (!keep.contains(strays.get(i))) {
                container.setItem(straySlots.get(i), ItemStack.EMPTY);
            }
        }
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
