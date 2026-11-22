package net.expedition.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Bosses don't drop finished gear: a boss listed here drops its heart instead of the listed items, and the heart is
 * crafted into one of them on Create machines. Data: {@code data/expedition/loot_modifiers/boss_hearts.json}.
 */
public final class BossLoot extends LootModifier {
    public record Boss(ResourceLocation entity, List<ResourceLocation> items) {
        static final Codec<Boss> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Boss::entity),
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(Boss::items)
        ).apply(instance, Boss::new));
    }

    public static final MapCodec<BossLoot> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
        .and(Boss.CODEC.listOf().fieldOf("bosses").forGetter(loot -> loot.bosses))
        .apply(instance, BossLoot::new));

    private final List<Boss> bosses;

    public BossLoot(LootItemCondition[] conditions, List<Boss> bosses) {
        super(conditions);
        this.bosses = bosses;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Entity killed = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (killed == null || !context.hasParam(LootContextParams.DAMAGE_SOURCE)) {
            return loot;
        }
        ResourceLocation type = EntityType.getKey(killed.getType());
        for (Boss boss : bosses) {
            if (boss.entity().equals(type)) {
                loot.removeIf(stack -> boss.items().contains(BuiltInRegistries.ITEM.getKey(stack.getItem())));
                loot.add(BossHeartItem.of(type));
                break;
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
