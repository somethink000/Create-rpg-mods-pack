package net.expedition.route;

import java.util.ArrayList;
import java.util.List;
import net.expedition.net.CutscenePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Story scenes (client: CutsceneClient): lines heard while the player plays on, or - for the prologue -
 * a cinematic shot of a place, letterboxed, the player safe meanwhile. Lines are lang keys {@code <base>.1},
 * {@code <base>.2}... one per speaker letter (see {@link Acts#speaker}), voiced where a recording exists; a title scene
 * shows {@code <base>.title} first.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = net.expedition.Expedition.MOD_ID)
public final class Cutscenes {
    private Cutscenes() {
    }

    /** Ticks a scene line lasts at most (the client may skip earlier): the player can't be hurt meanwhile. */
    private static final int TICKS_PER_LINE = 140;
    private static final java.util.Map<java.util.UUID, Long> SAFE_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    @net.neoforged.bus.api.SubscribeEvent
    static void onDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Long until = SAFE_UNTIL.get(player.getUUID());
            if (until != null && player.getServer().getTickCount() < until) {
                event.setCanceled(true);
            }
        }
    }

    public static void play(ServerPlayer player, BlockPos anchor, String base, String speakers, boolean titled) {
        play(player, anchor, base, speakers, titled, null);
    }

    /** With a camera shot (Shots): the scene is filmed along it, the player held and unhurt meanwhile; null: only heard. */
    public static void play(ServerPlayer player, BlockPos anchor, String base, String speakers, boolean titled, Shots.Path shot) {
        List<CutscenePayload.Line> lines = new ArrayList<>();
        for (int i = 0; i < speakers.length(); i++) {
            lines.add(new CutscenePayload.Line(Acts.speaker(speakers.charAt(i)), base + "." + (i + 1)));
        }
        if (player.connection != null && player.connection.hasChannel(CutscenePayload.TYPE)) {
            CutscenePayload.Shot path = shot == null ? CutscenePayload.Shot.AUTO : new CutscenePayload.Shot(vector(shot.from()), vector(shot.to()), -1);
            PacketDistributor.sendToPlayer(player, new CutscenePayload(shot == null ? anchor : BlockPos.containing(shot.focus()),
                titled ? base + ".title" : "", lines, shot != null, path));
        }
        if (shot == null) {
            return;
        }
        SAFE_UNTIL.put(player.getUUID(), (long) player.getServer().getTickCount() + (long) TICKS_PER_LINE * (lines.size() + 1));
    }

    private static org.joml.Vector3f vector(net.minecraft.world.phys.Vec3 v) {
        return new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}
