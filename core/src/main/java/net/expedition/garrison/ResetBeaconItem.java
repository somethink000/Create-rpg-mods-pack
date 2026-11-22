package net.expedition.garrison;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Used inside a cleared dungeon: rebuilds it and brings its garrison back one NG+ level stronger. Consumed. */
public final class ResetBeaconItem extends Item {
    public ResetBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        Garrisons.Dungeon dungeon = DungeonReset.clearedDungeonAt(serverLevel, player.blockPosition());
        if (dungeon == null) {
            player.displayClientMessage(Component.translatable("expedition.reset.not_here").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (DungeonReset.busy(dungeon.key())) {
            return InteractionResultHolder.fail(stack);
        }
        DungeonReset.start(serverLevel, dungeon);
        player.displayClientMessage(Component.translatable("expedition.reset.started").withStyle(ChatFormatting.DARK_RED), true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
