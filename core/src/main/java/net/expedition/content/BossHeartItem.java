package net.expedition.content;

import net.expedition.ExpeditionRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A boss's heart: the boss is stored in a component; a Create recipe turns it into one of that boss's items. */
public final class BossHeartItem extends Item {
    public BossHeartItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceLocation boss) {
        ItemStack stack = new ItemStack(ExpeditionRegistry.BOSS_HEART.get());
        stack.set(ExpeditionRegistry.BOSS.get(), boss);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation boss = stack.get(ExpeditionRegistry.BOSS.get());
        EntityType<?> type = boss == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(boss).orElse(null);
        return type == null ? super.getName(stack) : Component.translatable("item.expedition.boss_heart.of", type.getDescription());
    }
}
