package net.expedition.content;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.expedition.Expedition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Items that are only a stronger copy of something else (tag {@code expedition:removed}) are taken out of the game:
 * their recipes are dropped after every data load, a global loot modifier strips them from chests and mob drops, and
 * {@code c:hidden_from_recipe_viewers} hides them in JEI/EMI. Also drops every mod recipe that has a Create version
 * at {@code expedition:converted/<ns>/<path>}, so special gear is made on Create machines only.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class RemovedItems {
    private RemovedItems() {
    }

    private static final String CONVERTED = "converted/";
    private static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));

    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "removed"));

    /** Drops every recipe that makes a removed item. Tags must be bound, so this runs after they are. */
    public static int removeRecipes(MinecraftServer server) {
        var manager = server.getRecipeManager();
        var registries = server.registryAccess();
        // expedition:converted/<ns>/<path> is the Create version of <ns>:<path>: the original goes.
        Set<ResourceLocation> replaced = new HashSet<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ResourceLocation id = holder.id();
            if (id.getNamespace().equals(Expedition.MOD_ID) && id.getPath().startsWith(CONVERTED)) {
                String rest = id.getPath().substring(CONVERTED.length());
                int slash = rest.indexOf('/');
                replaced.add(ResourceLocation.fromNamespaceAndPath(rest.substring(0, slash), rest.substring(slash + 1)));
            }
        }
        List<RecipeHolder<?>> kept = manager.getRecipes().stream()
            .filter(holder -> !holder.value().getResultItem(registries).is(TAG) && !replaced.contains(holder.id()) && !isLadderBypass(holder.id()))
            .toList();
        int removed = manager.getRecipes().size() - kept.size();
        if (removed > 0) {
            manager.replaceRecipes(kept);
        }
        Expedition.LOG.info("Removed items: {} recipes dropped ({} replaced by Create versions)", removed, replaced.size());
        java.util.Set<Item> made = new java.util.HashSet<>();
        java.util.Map<Item, Item> smelted = new java.util.HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Item result = holder.value().getResultItem(registries).getItem();
            made.add(result);
            if (holder.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking && !cooking.getIngredients().isEmpty()) {
                // Prefer the raw material (raw iron) over ore blocks as the "source" of a smelting result.
                for (ItemStack input : cooking.getIngredients().getFirst().getItems()) {
                    Item current = smelted.get(result);
                    if (current == null || !current.getDefaultInstance().is(RAW_MATERIALS) && input.is(RAW_MATERIALS)) {
                        smelted.put(result, input.getItem());
                    }
                }
            }
        }
        ChestLoot.setRecipeIndex(made, smelted);
        DungeonLoot.rebuild(manager, registries);
        return removed;
    }

    /** Apotheosis smithing upgrades (stone → iron, gold → diamond tools) skip the Create recipes of the gear ladder. */
    private static boolean isLadderBypass(ResourceLocation id) {
        return id.getNamespace().equals("apotheosis") && id.getPath().startsWith("smithing/upgrade_");
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        removeRecipes(event.getServer());
    }

    /** /reload rebuilds recipes: drop them again before they are synced to players (player is null for /reload). */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            removeRecipes(event.getPlayerList().getServer());
        }
    }

    /** Global loot modifier {@code expedition:remove_items}: strips removed items from any loot. */
    public static final class LootRemover extends LootModifier {
        public static final MapCodec<LootRemover> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, LootRemover::new));

        public LootRemover(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(stack -> stack.is(TAG));
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
