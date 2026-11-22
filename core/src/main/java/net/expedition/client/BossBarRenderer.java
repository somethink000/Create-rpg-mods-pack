package net.expedition.client;

import net.expedition.Expedition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Every boss bar (bosses, guardians, garrisons; mods' own bars too: drawn first, then the event is cancelled) as one
 * thin line with the name written on it, white outlined in black so it reads over any colour.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID, value = Dist.CLIENT)
public final class BossBarRenderer {
    private BossBarRenderer() {
    }

    static final int WIDTH = 160;
    static final int HEIGHT = 7;
    /** The name's size on the bar: small enough to sit inside it. */
    static final float TEXT_SCALE = 0.75f;
    /** From one bar to the next. */
    static final int SPACING = 11;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        event.setCanceled(true);
        event.setIncrement(SPACING);
        BossEvent boss = event.getBossEvent();
        GuiGraphics graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int left = event.getX() + 91 - WIDTH / 2;
        int top = event.getY() - 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xC0000000);
        graphics.fill(left, top, left + (int) (WIDTH * Math.max(0, Math.min(1, boss.getProgress()))), top + HEIGHT, color(boss.getColor()));
        var name = boss.getName().getVisualOrderText();
        graphics.pose().pushPose();
        graphics.pose().translate(left + WIDTH / 2f, top + HEIGHT / 2f, 0);
        float scale = Math.min(TEXT_SCALE, (WIDTH - 6f) / Math.max(1, font.width(name))); // a long name shrinks to fit the bar
        graphics.pose().scale(scale, scale, 1);
        font.drawInBatch8xOutline(name, -font.width(name) / 2f, -font.lineHeight / 2f + 1, 0xFFFFFFFF, 0xFF000000, graphics.pose().last().pose(),
            graphics.bufferSource(), 0xF000F0);
        graphics.flush();
        graphics.pose().popPose();
    }

    static int color(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFE060A8;
            case BLUE -> 0xFF4C8CE0;
            case RED -> 0xFFD04040;
            case GREEN -> 0xFF50B848;
            case YELLOW -> 0xFFE0B830;
            case PURPLE -> 0xFFB05CE0;
            case WHITE -> 0xFFD8D8D8;
        };
    }
}
