package net.expedition.route;

import net.expedition.Expedition;
import net.expedition.content.StructureFacades;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Tree at the camp (LandingCamp, its withered wood unbreakable anyway) answers whoever starts hacking at it: lightning
 * strikes them dead and the Tree says one of its lines (lang {@code expedition.story.wrath.<n>.1}, voiced by gen_voice).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class TreeWrath {
    private TreeWrath() {
    }

    /** How many lines the Tree has for this (gen_voice.py reads this number). */
    static final int LINES = 3;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "tree_wrath"));

    @SubscribeEvent
    static void onHit(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
            || !(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator() || !player.isAlive()
            || !event.getLevel().getBlockState(event.getPos()).is(StructureFacades.WITHERED_WOOD.get())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Cutscenes.play(player, event.getPos(), "expedition.story.wrath." + (1 + level.random.nextInt(LINES)), "T", false);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.position());
            bolt.setVisualOnly(true); // the bolt is the show; the death is dealt below (no fire on the camp)
            level.addFreshEntity(bolt);
        }
        // Its own damage (data/expedition/damage_type): past armour and the few seconds' safety after respawning at the grave
        // right below the Tree; a totem still saves.
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE)), 1000);
        event.setCanceled(true);
    }
}
