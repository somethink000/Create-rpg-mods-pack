package net.expedition.compat;

import net.expedition.Expedition;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Performance testing without a person at the keyboard: with {@code -Dexpedition.framelog=true} the client logs, every
 * 5 seconds, the average FPS and the slowest frame of that window ("framelog: ..."). Off otherwise.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID, value = Dist.CLIENT)
public final class FrameLog {
    private FrameLog() {
    }

    private static final boolean ENABLED = Boolean.getBoolean("expedition.framelog");
    private static long windowStart;
    private static long lastFrame;
    private static long worstFrame;
    private static int frames;
    private static int over50;

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || Minecraft.getInstance().level == null) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) {
            long frame = now - lastFrame;
            worstFrame = Math.max(worstFrame, frame);
            if (frame > 50_000_000L) {
                over50++;
            }
        }
        lastFrame = now;
        frames++;
        if (windowStart == 0) {
            windowStart = now;
        } else if (now - windowStart >= 5_000_000_000L) {
            Expedition.LOG.info("framelog: fps {} worst {} ms, frames over 50 ms {}", Math.round(frames * 1e9 / (now - windowStart)), worstFrame / 1_000_000, over50);
            windowStart = now;
            frames = 0;
            worstFrame = 0;
            over50 = 0;
        }
    }
}
