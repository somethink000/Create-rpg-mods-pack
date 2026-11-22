package net.expedition.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.expedition.Expedition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Combat stats lined up with the route: a later boss's gear is stronger than an earlier one's. The table
 * ({@code /expedition/gear_balance.json} in the jar) is read on both sides, so tooltips and attack cooldowns match the server.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class GearBalance {
    private GearBalance() {
    }

    private record Stats(double first, double second) {
    }

    private static final Map<ResourceLocation, Stats> WEAPONS = new HashMap<>();
    private static final Map<ResourceLocation, Stats> ARMOR = new HashMap<>();
    private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "balance_armor");
    private static final ResourceLocation TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "balance_toughness");

    static {
        try (var reader = new InputStreamReader(GearBalance.class.getResourceAsStream("/expedition/gear_balance.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            read(root.getAsJsonObject("weapons"), WEAPONS);
            read(root.getAsJsonObject("armor"), ARMOR);
        } catch (Exception e) {
            throw new IllegalStateException("Expedition: bad gear_balance.json", e);
        }
    }

    private static void read(JsonObject table, Map<ResourceLocation, Stats> into) {
        table.entrySet().forEach(entry -> {
            var values = entry.getValue().getAsJsonArray();
            into.put(ResourceLocation.parse(entry.getKey()), new Stats(values.get(0).getAsDouble(), values.get(1).getAsDouble()));
        });
    }

    public static boolean isBalanced(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return WEAPONS.containsKey(id) || ARMOR.containsKey(id);
    }

    @SubscribeEvent
    static void onModifiers(ItemAttributeModifierEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        Stats weapon = WEAPONS.get(id);
        if (weapon != null) {
            event.removeAllModifiersFor(Attributes.ATTACK_DAMAGE);
            event.removeAllModifiersFor(Attributes.ATTACK_SPEED);
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, weapon.first() - 1,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, weapon.second() - 4,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            return;
        }
        Stats armor = ARMOR.get(id);
        if (armor != null) {
            EquipmentSlotGroup slot = event.getModifiers().stream().filter(entry -> entry.attribute().is(Attributes.ARMOR)).map(entry -> entry.slot())
                .findFirst().orElse(EquipmentSlotGroup.ARMOR);
            event.removeAllModifiersFor(Attributes.ARMOR);
            event.removeAllModifiersFor(Attributes.ARMOR_TOUGHNESS);
            event.addModifier(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, armor.first(), AttributeModifier.Operation.ADD_VALUE), slot);
            event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, armor.second(), AttributeModifier.Operation.ADD_VALUE), slot);
        }
    }
}
