package net.expedition.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.expedition.Expedition;
import net.expedition.net.CutscenePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Story scenes on the client. Lines are voiced in English in every game language, the text in the game's language
 * (recordings in assets/expedition/sounds/voice/en, see tools/story/gen_voice.py)
 * and typed out with the speaker's name; a line lasts as long as its recording, or its reading time without one.
 * <p>Voiced (most scenes): the lines show above the hotbar while the player plays on. Cinematic (the prologue, a boss's
 * lair): the camera moves straight along the way the server worked out (Shots: free of blocks, seeing the scene),
 * looking at it, faded in once its chunks are drawn; the screen is letterboxed, the HUD hidden and the player held.
 * Jump: next line; sneak: skip.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID, value = Dist.CLIENT)
public final class CutsceneClient {
    private CutsceneClient() {
    }

    private static final long TITLE_MS = 3500;
    private static final double CHARS_PER_SECOND = 38;
    /** Pause after a recording before the next line. */
    private static final long AFTER_VOICE_MS = 700;
    private static final String STORY_PREFIX = "expedition.story.";

    private static CutscenePayload scene;
    /** When the game was paused during the scene (0: not paused). */
    private static long pausedAt;
    /** A scene that came before the world was on screen. */
    private static CutscenePayload pending;
    private static Entity camera;
    private static long started;
    private static long lineStarted;
    private static long sceneMillis;
    private static int line = -1;
    private static boolean jumpWasDown;
    private static SoundInstance voice;
    /** The shot's camera way (from the server, Shots). */
    private static Vec3 pathFrom;
    private static Vec3 pathTo;
    /** After a scripted shot: the screen stays black until the next shot arrives, or this moment. */
    private static long holdUntil;
    private static final long FADE_IN_MS = 1200;
    private static final long FADE_OUT_MS = 900;
    private static final long HOLD_MS = 3000;
    /** A scripted shot waits (black) until the chunks around its place are in, then a moment for them to be drawn. */
    private static long waitingSince;
    private static long readyAt;
    private static final long SETTLE_MS = 2000;
    private static final long WAIT_MAX_MS = 15000;
    /** The loading screen (a cinematic scene without lines) holds until the first shot comes, at most this long. */
    private static final long LOADING_MAX_MS = 200_000;
    private static Map<String, Integer> durations;
    /** The voice language the durations were read for. */
    private static String durationsLanguage;

    public static void play(CutscenePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (!worldShown(mc)) {
            // Sent as the player joins (the prologue's loading screen): played once the world is on screen.
            pending = payload;
            return;
        }
        if (payload.cinematic() && (payload.lines().isEmpty() || payload.shot().sequence() >= 0)) {
            startMusic(mc);
        }
        // The camera of the shot before carries on into the next one (no flash of the player's own view between shots).
        if (payload.cinematic() && !payload.shot().scripted() && !payload.lines().isEmpty()) {
            payload = new CutscenePayload(payload.anchor(), payload.title(), payload.lines(), false, payload.shot());
        }
        Entity keep = payload.cinematic() ? camera : null;
        if (keep == null) {
            stop();
        } else {
            stopVoice();
        }
        scene = payload;
        holdUntil = 0;
        line = -1;
        pathFrom = pathTo = null;
        waitingSince = readyAt = 0;
        started = Util.getMillis();
        if (payload.cinematic()) {
            if (keep == null) {
                camera = EntityType.MARKER.create(mc.level);
                camera.setPos(mc.player.getEyePosition());
            }
            mc.setCameraEntity(camera);
        }
        if (loading()) {
            return;
        }
        if (payload.cinematic() && payload.shot().scripted()) {
            waitingSince = started;
            return;
        }
        begin(mc, started);
    }

    /** The loading screen before the prologue's first shot. */
    private static boolean loading() {
        return scene != null && scene.cinematic() && scene.lines().isEmpty();
    }

    /** The scene starts: the title, then the lines; the camera on its path. */
    private static void begin(Minecraft mc, long now) {
        waitingSince = readyAt = 0;
        started = now;
        boolean scripted = scene.cinematic() && scene.shot().scripted();
        lineStarted = started + Math.max(scene.title().isEmpty() ? 0 : TITLE_MS, scripted ? FADE_IN_MS : 0);
        sceneMillis = lineStarted - started;
        for (int i = 0; i < scene.lines().size(); i++) {
            sceneMillis += lineMillis(i);
        }
        if (scripted) {
            // The server's way: free of blocks all along and seeing the scene (Shots); the camera moves straight along it.
            pathFrom = new Vec3(scene.shot().from());
            pathTo = new Vec3(scene.shot().to());
            place(0);
        }
    }

    /** The chunks the shot sees (around its place, as far as the camera stands) have come from the server. */
    private static boolean placeLoaded(Minecraft mc) {
        int radius = Math.min(5, mc.options.getEffectiveRenderDistance() - 1);
        int cx = scene.anchor().getX() >> 4;
        int cz = scene.anchor().getZ() >> 4;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!mc.level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void stopVoice() {
        if (voice != null) {
            Minecraft.getInstance().getSoundManager().stop(voice);
            voice = null;
        }
    }

    /** Tells the server a scripted shot is over (or the scene skipped); it stages the next shot or ends. */
    private static void reportDone(boolean skipped) {
        if (scene != null && (scene.shot().sequence() >= 0 || loading())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new net.expedition.net.CutsceneDonePayload(scene.shot().sequence(), skipped));
        }
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (music != null) {
            music.fadeOut();
            music = null;
        }
        if (voice != null) {
            mc.getSoundManager().stop(voice);
            voice = null;
        }
        if (camera != null && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        scene = null;
        camera = null;
        pathFrom = pathTo = null;
        holdUntil = 0;
        waitingSince = readyAt = 0;
        pausedAt = 0;
    }

    /** Leaving the world: nothing of its scenes carries over into the next world loaded. */
    @SubscribeEvent
    static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        pending = null;
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
        }
        stop();
        music = null;
    }

    private static boolean worldShown(Minecraft mc) {
        return mc.level != null && mc.player != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)
            && !(mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen);
    }

    // ---- music -----------------------------------------------------------------------------------------------------

    /** The prologue's music: Minecraft's own "Alpha" (the credits theme), quiet under the voice, faded in and out. */
    private static final float MUSIC_VOLUME = 0.35f;
    private static final int MUSIC_FADE_TICKS = 60;
    private static PrologueMusic music;

    private static void startMusic(Minecraft mc) {
        if (music == null || !mc.getSoundManager().isActive(music)) {
            music = new PrologueMusic();
            mc.getSoundManager().play(music);
        }
    }

    /** The game's own music keeps quiet while the prologue's plays (no two tracks at once; null stops what plays). */
    @SubscribeEvent
    static void onSelectMusic(net.neoforged.neoforge.client.event.SelectMusicEvent event) {
        if (music != null) {
            event.setMusic(null);
        }
    }

    private static final class PrologueMusic extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private int age;
        private int fadingFrom = -1;

        PrologueMusic() {
            super(net.minecraft.sounds.SoundEvents.MUSIC_CREDITS.value(), SoundSource.MUSIC, RandomSource.create());
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            looping = true;
            volume = 0.001f;
        }

        void fadeOut() {
            fadingFrom = age;
        }

        @Override
        public void tick() {
            age++;
            float in = Math.min(1, age / (float) MUSIC_FADE_TICKS);
            float out = fadingFrom < 0 ? 1 : 1 - (age - fadingFrom) / (float) MUSIC_FADE_TICKS;
            volume = Math.max(0.001f, MUSIC_VOLUME * in * out);
            if (out <= 0) {
                stop();
            }
        }
    }

    // ---- voice ---------------------------------------------------------------------------------------------------

    /** Recordings exist in English only (the Russian voice was poor): every game hears them, under its own subtitles. */
    private static String voiceLanguage() {
        return "en";
    }

    /** The recording of a line: lang key expedition.story.act.1.2 -> sound expedition:voice.en.act.1.2. */
    private static ResourceLocation voiceOf(String key) {
        return key.startsWith(STORY_PREFIX)
            ? ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "voice." + voiceLanguage() + "." + key.substring(STORY_PREFIX.length())) : null;
    }

    private static Map<String, Integer> durations() {
        String language = voiceLanguage();
        if (durations == null || !language.equals(durationsLanguage)) {
            durations = new HashMap<>();
            durationsLanguage = language;
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "voice/durations_" + language + ".json");
            Minecraft.getInstance().getResourceManager().getResource(file).ifPresent(resource -> {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    json.entrySet().forEach(entry -> durations.put(entry.getKey(), entry.getValue().getAsInt()));
                } catch (Exception e) {
                    Expedition.LOG.warn("Can't read voice durations", e);
                }
            });
        }
        return durations;
    }

    /** A line lasts as long as its recording (and a breath), or its reading time when it has none. */
    private static long lineMillis(int index) {
        String key = scene.lines().get(index).text();
        Integer recorded = durations().get(key);
        if (recorded != null) {
            return recorded + AFTER_VOICE_MS;
        }
        return 2200 + (long) (Component.translatable(key).getString().length() * 1000 / CHARS_PER_SECOND);
    }

    private static void startLine(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (voice != null) {
            mc.getSoundManager().stop(voice);
            voice = null;
        }
        line = index;
        lineStarted = Util.getMillis();
        if (index >= scene.lines().size()) {
            return;
        }
        ResourceLocation sound = voiceOf(scene.lines().get(index).text());
        if (sound != null && durations().containsKey(scene.lines().get(index).text()) && mc.getSoundManager().getSoundEvent(sound) != null) {
            voice = new SimpleSoundInstance(sound, SoundSource.VOICE, 1, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            mc.getSoundManager().play(voice);
        }
    }

    // ---- camera ----------------------------------------------------------------------------------------------------

    private static Vec3 target() {
        return Vec3.atCenterOf(scene.anchor());
    }

    /** The camera on its slow approach, looking at the place. */
    private static void place(long elapsed) {
        double progress = Mth.clamp(elapsed / (double) Math.max(1, sceneMillis), 0, 1);
        double eased = progress * progress * (3 - 2 * progress);
        Vec3 eye = pathFrom.lerp(pathTo, eased);
        Vec3 look = target().subtract(eye);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        camera.setPos(eye);
        camera.xo = eye.x;
        camera.yo = eye.y;
        camera.zo = eye.z;
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
    }

    // ---- events ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (scene == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        long now = Util.getMillis();
        if (mc.isPaused()) {
            // Paused (the game menu): the voice is paused too, so the scene's clock stands still with it.
            pausedAt = pausedAt == 0 ? now : pausedAt;
            return;
        }
        if (pausedAt != 0) {
            long paused = now - pausedAt;
            pausedAt = 0;
            started += paused;
            lineStarted += paused;
            holdUntil = holdUntil > 0 ? holdUntil + paused : 0;
            readyAt = readyAt > 0 ? readyAt + paused : 0;
            waitingSince = waitingSince > 0 ? waitingSince + paused : 0;
        }
        if (holdUntil > 0) {
            if (now > holdUntil) {
                stop();
            }
            return;
        }
        if (loading()) {
            if (now - started > LOADING_MAX_MS) {
                stop();
            }
            return;
        }
        if (waitingSince > 0) {
            if (readyAt == 0 && (placeLoaded(mc) || now - waitingSince > WAIT_MAX_MS)) {
                // Chunks already here (a scene where the player stands): at once; just come: a moment to draw them.
                readyAt = now + (now - waitingSince < 250 ? 0 : SETTLE_MS);
            }
            if (readyAt == 0 || now < readyAt) {
                return;
            }
            begin(mc, now);
        }
        if (line < 0 && now >= lineStarted) {
            startLine(0);
        } else if (line >= 0 && line < scene.lines().size() && now - lineStarted > lineMillis(line)) {
            startLine(line + 1);
        }
        if (line >= scene.lines().size()) {
            if (scene.shot().sequence() >= 0) {
                reportDone(false);
                holdUntil = now + HOLD_MS;
                return;
            }
            stop();
            return;
        }
        if (camera != null) {
            if (mc.getCameraEntity() != camera) {
                mc.setCameraEntity(camera);
            }
            place(now - started);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pending != null && worldShown(mc)) {
            CutscenePayload payload = pending;
            pending = null;
            play(payload);
        }
        if (scene == null || camera == null) {
            return;
        }
        boolean jump = mc.options.keyJump.isDown();
        if (jump && !jumpWasDown && line >= 0) {
            startLine(line + 1);
        }
        jumpWasDown = jump;
        if (mc.options.keyShift.isDown() && Util.getMillis() - started > 500) {
            reportDone(true);
            stop();
        }
    }

    /**
     * The scene's view keeps the player's set field of view: the spectator player carried around the shots flies, and
     * whenever the view falls back to them for a frame the flying FOV boost pulled the zoom in and out like sprinting.
     */
    @SubscribeEvent
    static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (scene != null && camera != null && event.usedConfiguredFov()) {
            event.setFOV(Minecraft.getInstance().options.fov().get());
        }
    }

    @SubscribeEvent
    static void onMovement(MovementInputUpdateEvent event) {
        if (scene != null && camera != null) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (scene != null && camera != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** The HUD (hotbar, health, chat...) is hidden during a cinematic scene; a voiced scene leaves it be. */
    @SubscribeEvent
    static void onLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (scene != null && camera != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        if (scene == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = g.guiWidth();
        int height = g.guiHeight();
        boolean cinematic = camera != null;
        long time = Util.getMillis();
        if (loading() || waitingSince > 0) {
            g.fill(0, 0, width, height, 0xFF000000);
            if (loading()) {
                String dots = ".".repeat((int) (time / 500 % 4));
                g.drawCenteredString(font, Component.translatable(scene.title()).append(dots).withStyle(ChatFormatting.GOLD), width / 2, height / 2 - 4, 0xFFFFFFFF);
                g.drawString(font, Component.translatable("expedition.story.skip").withStyle(ChatFormatting.DARK_GRAY), 6, height - 14, 0xFFFFFFFF);
            }
            return;
        }
        if (pathFrom != null) {
            // Scripted shots fade in from black and out to it; between shots the screen stays black.
            long sinceStart = time - started;
            long untilEnd = started + sceneMillis - time;
            float black = holdUntil > 0 ? 1 : Math.max(1 - sinceStart / (float) FADE_IN_MS, 1 - untilEnd / (float) FADE_OUT_MS);
            if (black > 0) {
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (Mth.clamp(black, 0, 1) * 255) << 24);
            }
            if (holdUntil > 0) {
                return;
            }
        }
        int bar = cinematic ? height / 8 : 0;
        if (cinematic) {
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);
        }
        long now = Util.getMillis();
        if (!scene.title().isEmpty() && now - started < TITLE_MS) {
            float alpha = Mth.clamp(Math.min((now - started) / 600f, (TITLE_MS - (now - started)) / 600f), 0, 1);
            int a = Math.max(4, (int) (alpha * 255)) << 24;
            g.pose().pushPose();
            g.pose().scale(2, 2, 1);
            g.drawCenteredString(font, Component.translatable(scene.title()).withStyle(ChatFormatting.GOLD), width / 4, cinematic ? height / 4 - 8 : height / 10, 0xFFFFFF | a);
            g.pose().popPose();
        }
        if (line < 0 || line >= scene.lines().size()) {
            return;
        }
        CutscenePayload.Line current = scene.lines().get(line);
        Component speaker = Component.translatable(current.speaker()).withStyle(speakerColor(current.speaker()));
        String text = Component.translatable(current.text()).getString();
        int shown = (int) Math.min(text.length(), (now - lineStarted) * CHARS_PER_SECOND / 1000);
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text.substring(0, shown)), Math.min(width - 40, 360));
        // Voiced lines sit above the hotbar and the health bars; a cinematic line just above the lower bar.
        int bottom = cinematic ? height - bar - 14 : height - 64;
        int y = bottom - wrapped.size() * 10;
        if (!cinematic) {
            int boxWidth = Math.min(width - 40, 360) + 16;
            g.fill(width / 2 - boxWidth / 2, y - 16, width / 2 + boxWidth / 2, bottom + 4, 0x90000000);
        }
        g.drawCenteredString(font, speaker, width / 2, y - 12, 0xFFFFFFFF);
        for (FormattedCharSequence row : wrapped) {
            g.drawCenteredString(font, row, width / 2, y, 0xFFE8E0D0);
            y += 10;
        }
        if (cinematic) {
            g.drawString(font, Component.translatable("expedition.story.skip").withStyle(ChatFormatting.DARK_GRAY), 6, height - bar + 4, 0xFFFFFFFF);
        }
    }

    private static ChatFormatting speakerColor(String speaker) {
        return speaker.endsWith("tree") ? ChatFormatting.GREEN : speaker.endsWith("log") ? ChatFormatting.GRAY : ChatFormatting.AQUA;
    }
}
