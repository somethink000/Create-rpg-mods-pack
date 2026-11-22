package net.expedition;

import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.expedition.content.BossHeartItem;
import net.expedition.content.BossLoot;
import net.expedition.content.ChestLoot;
import net.expedition.content.RemovedItems;
import net.expedition.content.StructureFacades;
import net.expedition.garrison.ResetBeaconItem;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Items and data components of the pack core. */
public final class ExpeditionRegistry {
    private ExpeditionRegistry() {
    }

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Expedition.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Expedition.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Expedition.MOD_ID);

    /** Which expedition this journal keeps (progress lives in the world's route data under this id). */
    /** Legacy: the old atlas book's route id, kept only so old books load and hand their progress over (see Route.journalOf). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> JOURNAL_ID = COMPONENTS.registerComponentType("journal_id",
        builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** Which boss a boss heart came from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> BOSS = COMPONENTS.registerComponentType("boss",
        builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));


    public static final DeferredItem<BossHeartItem> BOSS_HEART = ITEMS.registerItem("boss_heart", BossHeartItem::new,
        new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());

    /** Garrison trophies: unique to dungeon mobs (by family), the inputs of every special craft. */
    public static final java.util.List<DeferredItem<Item>> TROPHIES = java.util.stream.Stream.of(
        "grave_dust", "chitin_plate", "raider_insignia", "abyssal_scale", "cinder_core", "void_shard", "feral_fang")
        .map(name -> ITEMS.registerSimpleItem(name, new Item.Properties().rarity(Rarity.UNCOMMON))).toList();

    /**
     * Gun ammo of the later chapters for Create: Gunsmithing (its weapons take them: data/cgs/weapons): per gun, rounds of
     * black steel (chapter IV), ignitium (V) and cursium (VI-VII). Eight are mixed in a heated basin from eight rounds of
     * the tier before and one boss metal ingot, or its renewable stand-in: netherite scrap, a netherite ingot, a nether
     * star (data/expedition/recipe/ammo).
     */
    public static final java.util.List<DeferredItem<Item>> AMMO = java.util.stream.Stream.of("revolver", "gatling", "shotgun")
        .flatMap(gun -> java.util.stream.Stream.of("black_steel", "ignitium", "cursium").map(tier -> "round_" + gun + "_" + tier))
        .map(name -> ITEMS.registerSimpleItem(name, new Item.Properties())).toList();
    public static final DeferredItem<ResetBeaconItem> RESET_BEACON = ITEMS.registerItem("reset_beacon", ResetBeaconItem::new,
        new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    private static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Expedition.MOD_ID);

    /** Every item of the pack core in one creative tab: the fireseed, trophies, beacons and each boss's heart. */
    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> TAB = TABS.register("expedition",
        () -> net.minecraft.world.item.CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.expedition"))
            .icon(() -> new net.minecraft.world.item.ItemStack(net.expedition.route.StoryItems.GUIDING_SEED.get()))
            .displayItems((parameters, output) -> {
                output.accept(net.expedition.route.StoryItems.GUIDING_SEED.get());
                output.accept(RESET_BEACON.get());
                AMMO.forEach(round -> output.accept(round.get()));
                TROPHIES.forEach(trophy -> output.accept(trophy.get()));
                // The route's bosses (known where the server's data is: single player, or an operator's own server).
                java.util.List<ResourceLocation> bosses = net.expedition.route.RouteSteps.all().stream()
                    .flatMap(entry -> entry.step().boss().stream()).distinct().toList();
                if (bosses.isEmpty()) {
                    output.accept(BOSS_HEART.get());
                }
                bosses.forEach(boss -> output.accept(BossHeartItem.of(boss)));
            }).build());

    static {
        LOOT_MODIFIERS.register("remove_items", () -> RemovedItems.LootRemover.CODEC);
        LOOT_MODIFIERS.register("chest_loot", () -> ChestLoot.CODEC);
        LOOT_MODIFIERS.register("boss_hearts", () -> BossLoot.CODEC);
    }

    static void register(IEventBus modBus) {
        StructureFacades.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
    }
}
