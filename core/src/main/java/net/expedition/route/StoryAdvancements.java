package net.expedition.route;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancement tree "The Stitched World" (data/expedition/advancement/story): acts, steps and the finale have an
 * "impossible" criterion and are awarded here; seals are inventory advancements of their own.
 */
public final class StoryAdvancements {
    private StoryAdvancements() {
    }

    public static void award(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("expedition", path));
        if (advancement == null) {
            return;
        }
        for (String criterion : player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
