package net.expedition.route;

import java.util.ArrayList;
import java.util.List;
import net.expedition.Expedition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The fireseed. */
public final class StoryItems {
    private StoryItems() {
    }

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Expedition.MOD_ID);
    /** Thrown into a lit campfire: shows the way to the nearest story place (see GuidingSeed). */
    public static final DeferredItem<Item> GUIDING_SEED = ITEMS.registerItem("guiding_seed", properties -> new Item(properties) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                                    net.minecraft.world.item.TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.translatable("item.expedition.guiding_seed.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
