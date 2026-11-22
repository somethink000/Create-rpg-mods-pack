package net.expedition.route;

import java.util.HashMap;
import java.util.Map;
import net.expedition.Expedition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * No boss kills a well-geared player in one blow: a player in their chapter's armor (ChapterGear, or better) loses at
 * most a third of their health to any one hit of a story boss or guardian (its special attacks too), so it takes three;
 * in weaker armor at most {@link #UNGEARED} of it (two hits). The boss's chapter is its route step's (guardians: their
 * step's; other tagged bosses: the last chapter). And the other way: no blow takes more than {@link #MAX_HIT_SHARE} of a
 * boss's health (Big Cannons, missiles) and explosions hurt it {@link #EXPLOSION_SHARE} as much, so a boss is fought, not
 * shelled (a player's own swing is not held back). Its health is its step's (Guardians, from the trial's measured hits: ChapterGear's sword, the chapter's time).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class BossHits {
    private BossHits() {
    }

    static final float GEARED = 0.34f;
    static final float UNGEARED = 0.6f;
    static final float MAX_HIT_SHARE = 0.06f;
    static final float EXPLOSION_SHARE = 0.35f;
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private static Map<EntityType<?>, Integer> acts;
    private static java.util.List<RouteSteps.Entry> actsFor;

    /** The chapter of a boss, or 0 if it is none. */
    static int actOf(Entity entity) {
        String guarded = Guardians.stepOf(entity);
        if (guarded != null) {
            return RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(guarded)).findFirst().map(entry -> Acts.actOf(entry.step())).orElse(Acts.LAST);
        }
        if (acts == null || actsFor != RouteSteps.all()) {
            Map<EntityType<?>, Integer> next = new HashMap<>();
            for (RouteSteps.Entry entry : RouteSteps.all()) {
                entry.step().boss().flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                    .ifPresent(type -> next.merge(type, Acts.actOf(entry.step()), Math::min));
            }
            acts = next;
            actsFor = RouteSteps.all();
        }
        Integer act = acts.get(entity.getType());
        return act != null ? act : entity.getType().is(BOSSES) ? Acts.LAST : 0;
    }

    /** A boss: a route step's boss or a guardian. */
    static boolean isBoss(Entity entity) {
        return Guardians.stepOf(entity) != null || StoryBosses.isBoss(entity.getType());
    }

    @SubscribeEvent
    static void onBossDamage(LivingDamageEvent.Pre event) {
        // Not /kill or the void: what bypasses invulnerability is no blow.
        if (event.getEntity() instanceof ServerPlayer || !isBoss(event.getEntity())
            || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float damage = event.getNewDamage();
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            damage *= EXPLOSION_SHARE;
        }
        // A player's own swing is the fight itself: only shells, bolts and blasts are held to the share.
        boolean melee = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.player.Player;
        event.setNewDamage(melee ? damage : Math.min(damage, event.getEntity().getMaxHealth() * MAX_HIT_SHARE));
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        int act = source == null || source == player ? 0 : actOf(source);
        if (act == 0) {
            return;
        }
        boolean geared = player.getArmorValue() >= ChapterGear.armor(act) - 0.5f
            && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= ChapterGear.toughness(act) - 0.5f;
        float cap = player.getMaxHealth() * (geared ? GEARED : UNGEARED);
        if (event.getNewDamage() > cap) {
            event.setNewDamage(cap);
        }
    }
}
