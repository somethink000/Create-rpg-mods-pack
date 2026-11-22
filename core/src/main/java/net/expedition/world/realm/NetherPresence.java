package net.expedition.world.realm;

import net.expedition.Expedition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Incendium runs its mobs from a datapack clock: every tick several selectors scan every loaded entity of the world,
 * a millisecond and more with a few thousand entities, even when none of its mobs is near anyone. Its mobs live in the
 * Nether realm only: once a second this sets {@code $expedition_nether} in its {@code in.dummy} objective to 1 while a
 * player is in or near that realm (0 otherwise), and the clock (kubejs/data/incendium/function/clocks) runs its mob
 * passes only then. Outside the realm world it is always 1 (Incendium as it was).
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class NetherPresence {
    private NetherPresence() {
    }

    private static final ScoreHolder FLAG = ScoreHolder.forNameOnly("$expedition_nether");
    private static final int NEAR = 192;

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective("in.dummy");
        if (objective == null) {
            return;
        }
        boolean near = RealmWorld.realmAt(server.overworld(), server.overworld().getSharedSpawnPos()) == null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (near) {
                break;
            }
            if (player.level() != server.overworld()) {
                near = true; // another dimension: let Incendium look as it always did
                break;
            }
            int x = Mth.floor(player.getX());
            int z = Mth.floor(player.getZ());
            for (int[] d : new int[][] {{0, 0}, {NEAR, 0}, {-NEAR, 0}, {0, NEAR}, {0, -NEAR}}) {
                if (RealmLayout.sample(x + d[0], z + d[1]).weightOf(Realm.NETHER) > 0) {
                    near = true;
                    break;
                }
            }
        }
        scoreboard.getOrCreatePlayerScore(FLAG, objective).set(near ? 1 : 0);
    }
}
