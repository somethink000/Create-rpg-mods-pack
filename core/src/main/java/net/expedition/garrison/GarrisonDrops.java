package net.expedition.garrison;

import java.util.List;
import net.expedition.Expedition;
import net.expedition.content.ChestLoot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Garrison mobs carry Create parts of their dungeon's tier (by its difficulty, like chest loot: ChestLoot.tierDistance). Only garrison
 * mobs killed by a player drop them, so mob farms don't. Boss materials stay with the bosses.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class GarrisonDrops {
    private GarrisonDrops() {
    }

    static final double PART_CHANCE = 0.35;
    static final double MECHANISM_CHANCE = 0.04;
    static final double SPECIAL_CHANCE = 0.4;
    static final double TROPHY_CHANCE = 0.3;

    /** Trophy by family: dimension or realm first (Nether, End), then undead, arthropod, raider, aquatic, else beast. */
    public static Item trophyFor(LivingEntity entity) {
        var realm = net.expedition.world.realm.RealmWorld.realmAt(entity.level(), entity.blockPosition());
        String name = entity.level().dimension() == net.minecraft.world.level.Level.NETHER || realm == net.expedition.world.realm.Realm.NETHER ? "cinder_core"
            : entity.level().dimension() == net.minecraft.world.level.Level.END || realm == net.expedition.world.realm.Realm.END ? "void_shard"
            : entity.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD) ? "grave_dust"
            : entity.getType().is(net.minecraft.tags.EntityTypeTags.ARTHROPOD) ? "chitin_plate"
            : entity.getType().is(net.minecraft.tags.EntityTypeTags.RAIDERS) || entity.getType().is(net.minecraft.tags.EntityTypeTags.ILLAGER) ? "raider_insignia"
            : entity.getType().is(net.minecraft.tags.EntityTypeTags.AQUATIC) ? "abyssal_scale" : "feral_fang";
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, name));
    }
    /** NG+: +10% part chance per level. */
    static final double NG_PART_BONUS = 0.10;

    private record Part(String id, int min, int max) {
    }

    private record Tier(double maxDistance, List<Part> parts, String mechanism) {
    }

    // What a player builds with at each tier (difficulty on the loot scale; as chests: no TFMG steel, it is the blast furnace's to make).
    private static final List<Tier> TIERS = List.of(
        new Tier(1400, List.of(new Part("create:andesite_alloy", 1, 3), new Part("create:iron_sheet", 1, 1), new Part("create:cogwheel", 1, 1),
            new Part("create:shaft", 1, 2)), null),
        new Tier(2200, List.of(new Part("create:brass_nugget", 2, 4), new Part("create:electron_tube", 1, 1), new Part("create:copper_sheet", 1, 1),
            new Part("minecraft:gunpowder", 1, 2)), null),
        new Tier(3200, List.of(new Part("create:brass_ingot", 1, 1), new Part("create:brass_sheet", 1, 1), new Part("create:iron_sheet", 1, 2),
            new Part("createbigcannons:powder_charge", 1, 1)), "create:precision_mechanism"),
        new Tier(Double.MAX_VALUE, List.of(new Part("create:brass_sheet", 1, 2), new Part("create:sturdy_sheet", 1, 1),
            new Part("createbigcannons:powder_charge", 1, 2), new Part("create:electron_tube", 1, 2)), "create:precision_mechanism"));

    static Tier tierAt(double distance) {
        return TIERS.stream().filter(tier -> distance < tier.maxDistance()).findFirst().orElse(TIERS.getLast());
    }

    private static ItemStack stack(String id, int count) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** The part (or nothing) a normal garrison mob drops at this distance from spawn. */
    public static ItemStack roll(RandomSource random, double distance) {
        return roll(random, distance, PART_CHANCE, MECHANISM_CHANCE);
    }

    static ItemStack roll(RandomSource random, double distance, double partChance, double mechanismChance) {
        Tier tier = tierAt(distance);
        if (tier.mechanism() != null && random.nextDouble() < mechanismChance) {
            return stack(tier.mechanism(), 1);
        }
        if (random.nextDouble() >= partChance) {
            return ItemStack.EMPTY;
        }
        Part part = tier.parts().get(random.nextInt(tier.parts().size()));
        return stack(part.id(), part.min() + random.nextInt(part.max() - part.min() + 1));
    }

    /** The part a garrison mob drops (or nothing), more likely in NG+. */
    public static List<ItemStack> drops(RandomSource random, double distance, int ng) {
        return List.of(roll(random, distance, Math.min(1, PART_CHANCE + NG_PART_BONUS * ng), MECHANISM_CHANCE));
    }

    @SubscribeEvent
    static void onExperience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.getPersistentData().getString(Garrisons.TAG).isEmpty() || event.getAttackingPlayer() == null) {
            return;
        }
        event.setDroppedExperience(Math.max(event.getDroppedExperience(), 5));
    }

    /** Artifacts and relics are crafted (Create); no mob drops them (Artifacts' cows, relic loot modifiers). */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    static void noCraftedTreasureFromMobs(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // a player's own artifacts go to the grave with the rest
        }
        event.getDrops().removeIf(drop -> {
            String namespace = BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).getNamespace();
            return namespace.equals("artifacts") || namespace.equals("relics");
        });
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.getPersistentData().getString(Garrisons.TAG).isEmpty()
            || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        String key = entity.getPersistentData().getString(Garrisons.TAG);
        GarrisonRecord record = GarrisonData.get(level).find(key);
        int ng = record == null ? 0 : record.ng;
        List<ItemStack> loot = new java.util.ArrayList<>(drops(level.random, ChestLoot.tierDistance(level, entity.position()), ng));
        // The last kill (the death that emptied the bar has been counted already) carries what is left of the dungeon's loot.
        boolean last = record != null && record.remaining() <= 0;
        // Trophies: the route dungeon's own set, otherwise by the mob's family.
        int trophies = level.random.nextDouble() < TROPHY_CHANCE ? 1 : 0;
        if (trophies > 0) {
            List<Item> pool = net.expedition.route.Route.trophiesFor(level.getServer(), key);
            for (int i = 0; i < trophies; i++) {
                loot.add(new ItemStack(pool.isEmpty() ? trophyFor(entity) : pool.get(level.random.nextInt(pool.size()))));
            }
        }
        // The dungeon's special loot (taken from its chests) is carried by the garrison: a piece sometimes, all that is
        // left on the last kill.
        if (record != null && !record.specialDrops.isEmpty()) {
            int pieces = last ? record.specialDrops.size() : level.random.nextDouble() < SPECIAL_CHANCE ? 1 : 0;
            for (int i = 0; i < pieces && !record.specialDrops.isEmpty(); i++) {
                loot.add(record.specialDrops.remove(level.random.nextInt(record.specialDrops.size())));
            }
            GarrisonData.get(level).setDirty();
        }
        // Experience to carry home (Create's nuggets): a handful for the dungeon's last defender.
        int nuggets = last ? 4 + level.random.nextInt(3) : 0;
        if (nuggets > 0) {
            loot.add(stack("create:experience_nugget", nuggets));
        }
        for (ItemStack part : loot) {
            if (!part.isEmpty()) {
                event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), part));
            }
        }
    }
}
