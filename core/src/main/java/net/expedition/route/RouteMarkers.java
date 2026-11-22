package net.expedition.route;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a story boss fell is marked on the map (a skull) of every player, online or not, whether they have been that
 * way or not: the fireseed leads only to the bosses still standing, so the map keeps the fallen ones. A landmark of the
 * player's own shows anywhere on their atlas (a world's shared one only in chunks they explored); players who join later
 * get theirs on login. Also removes the dungeon markers that worlds from before the fireseed still carry.
 */
public final class RouteMarkers {
    private RouteMarkers() {
    }

    /** The atlas picks the texture from the id's leading path: a skull for a boss, a red cross for a dungeon. */
    private static final String DUNGEON = "custom/red_x_large/expedition/";
    private static final String BOSS = "custom/skull/expedition/";
    private static final String FALLEN = "custom/skull/expedition/fallen/";

    public static ResourceLocation id(UUID journal, String step) {
        return id(journal, step, false);
    }

    public static ResourceLocation id(UUID journal, String step, boolean boss) {
        return ResourceLocation.fromNamespaceAndPath("antique_atlas", (boss ? BOSS : DUNGEON) + journal + "/" + step);
    }

    static void clear(ServerLevel level, UUID journal, String step) {
        WorldLandmarks landmarks = WorldLandmarks.of(level);
        if (landmarks != null) {
            landmarks.removeAll(landmark -> landmark.id().equals(id(journal, step, false)) || landmark.id().equals(id(journal, step, true)));
        }
    }

    /** A boss fell at its step's place: every player online gets it on their map (the others on login). */
    static void markFallen(MinecraftServer server, String step, RouteData.Target target) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            mark(player, step, target);
        }
    }

    /** On login: every fallen boss of the player's story on their map. */
    static void markAll(ServerPlayer player, RouteData.Progress progress) {
        progress.fallen.forEach((step, target) -> mark(player, step, target));
    }

    private static void mark(ServerPlayer player, String step, RouteData.Target target) {
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(target.dimension)));
        WorldLandmarks landmarks = level == null ? null : WorldLandmarks.of(level);
        if (landmarks == null) {
            return;
        }
        UUID owner = Surveyor.getUuid(player);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("antique_atlas", FALLEN + step);
        if (landmarks.contains(owner, id)) {
            return;
        }
        var title = RouteSteps.all().stream().filter(entry -> Route.id(entry).equals(step)).findFirst().map(Route::title)
            .orElse(net.minecraft.network.chat.Component.literal(step));
        landmarks.put(Landmark.create(owner, id, builder -> builder
            .add(LandmarkComponentTypes.POS, target.pos)
            .add(LandmarkComponentTypes.NAME, title)));
    }
}
