package net.expedition.route;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The expedition guide (a Patchouli book, expedition:guide): handed to every player once, on first join. */
public final class StoryBook {
    private StoryBook() {
    }

    public static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("expedition", "guide");

    @SuppressWarnings("unchecked")
    public static ItemStack stack() {
        Item book = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"));
        var type = (DataComponentType<ResourceLocation>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
        ItemStack stack = new ItemStack(book);
        if (type != null) {
            stack.set(type, BOOK);
        }
        return stack;
    }

    static void give(ServerPlayer player) {
        ItemStack book = stack();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}
