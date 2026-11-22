package net.expedition.client;

import net.expedition.Expedition;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * Each realm of the one world has its own air (and each Overworld biome its own tint: see Tint), as its former dimension had: the Nether no sun, no clouds, a sky and haze
 * of its biome's colour and a warm dim light; the Otherside a black sky and thick fog; the End a violet dusk; the
 * Twilight Forest an endless twilight; the Aether a bright sky. The Overworld keeps its own. Read at the camera from the
 * realm layout (blended over realm borders) and the biome there, eased over a few seconds so borders and biomes fade.
 * Sky colour (ClientLevelSkyMixin), sun, moon and stars (LevelRendererSkyMixin), clouds, light and fog are adjusted.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID, value = Dist.CLIENT)
public final class RealmAtmosphere {
    private RealmAtmosphere() {
    }

    /** A realm's air: sky and fog colour, light tint and floor, how much sun, clouds and fog distance it keeps. */
    private record Air(Vec3 sky, Vec3 fog, Vec3 tint, float ambient, float sun, float clouds, float fogFar) {
    }

    /**
     * An Overworld biome's air: tints over the Overworld's own sky and fog (day and night stay), a light tint and how far
     * the fog reaches. A swamp is murky green, a desert hazy and warm, snow cold and pale, a volcano ashen...
     */
    private record Tint(Vec3 sky, Vec3 fog, Vec3 light, float fogFar) {
    }

    private static final Tint PLAIN = new Tint(new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1), 1);
    private static final java.util.Map<net.minecraft.resources.ResourceKey<Biome>, Tint> TINTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Tint t(double sr, double sg, double sb, double fr, double fg, double fb, double lr, double lg, double lb, double far) {
        return new Tint(new Vec3(sr, sg, sb), new Vec3(fr, fg, fb), new Vec3(lr, lg, lb), (float) far);
    }

    private static boolean is(net.minecraft.core.Holder<Biome> biome, String tag) {
        return biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", tag)));
    }

    /** The biome's tint, by its kind (common tags, then its name: Terralith's and the mods' biomes as well as vanilla's). */
    private static Tint tint(net.minecraft.core.Holder<Biome> biome) {
        String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        if (name.contains("volcan") || name.contains("caldera") || name.contains("ash") || name.contains("basalt")) {
            return t(0.75, 0.70, 0.68, 0.82, 0.66, 0.56, 1.05, 0.92, 0.85, 0.6);
        }
        if (name.contains("irradiated")) {
            return t(0.90, 1.05, 0.80, 0.85, 1.10, 0.70, 0.95, 1.08, 0.85, 0.6);
        }
        if (name.contains("abyss")) {
            return t(0.90, 0.95, 1.05, 0.50, 0.60, 0.80, 0.85, 0.90, 1.0, 0.6);
        }
        if (name.contains("yellowstone")) {
            return t(1.05, 1.03, 0.90, 1.10, 1.08, 0.85, 1.02, 1.0, 0.92, 0.7);
        }
        if (name.contains("cloud") || name.contains("haze") || name.contains("mist") || name.contains("fog")) {
            return t(0.97, 1.0, 1.03, 1.05, 1.05, 1.07, 1.0, 1.0, 1.0, 0.6);
        }
        if (name.contains("skylands") || name.contains("alpha_islands") || name.contains("mirage")) {
            return t(1.02, 1.0, 1.08, 1.03, 1.0, 1.08, 1.03, 1.02, 1.05, 1);
        }
        if (name.contains("oasis")) {
            return t(1.04, 1.02, 0.92, 1.06, 1.02, 0.90, 1.03, 1.01, 0.94, 0.9);
        }
        if (name.contains("scarlet") || name.contains("autumn") || name.contains("maple")) {
            return t(1.06, 0.95, 0.92, 1.08, 0.94, 0.88, 1.04, 0.98, 0.94, 0.9);
        }
        if (name.contains("deep_dark")) {
            return t(1, 1, 1, 0.30, 0.34, 0.40, 0.70, 0.75, 0.86, 0.45);
        }
        if (name.contains("lush")) {
            return t(0.95, 1.05, 0.95, 0.80, 1.00, 0.80, 0.95, 1.05, 0.95, 0.8);
        }
        if (name.contains("dripstone")) {
            return t(1, 1, 1, 0.92, 0.86, 0.76, 1.0, 0.95, 0.90, 0.75);
        }
        if (is(biome, "is_swamp") || name.contains("swamp") || name.contains("marsh") || name.contains("mangrove") || name.contains("bayou")) {
            return t(0.78, 0.86, 0.72, 0.70, 0.80, 0.60, 0.92, 1.0, 0.88, 0.6);
        }
        if (is(biome, "is_mushroom") || name.contains("mushroom")) {
            return t(1.0, 0.82, 1.05, 0.95, 0.80, 1.0, 1.0, 0.92, 1.05, 0.8);
        }
        if (name.contains("cherry") || name.contains("sakura") || name.contains("blooming")) {
            return t(1.08, 0.90, 1.0, 1.10, 0.90, 0.98, 1.03, 0.97, 1.0, 0.95);
        }
        if (name.contains("moonlight")) {
            return t(0.85, 0.88, 1.08, 0.82, 0.86, 1.05, 0.86, 0.89, 1.02, 0.85);
        }
        if (name.contains("lavender") || name.contains("amethyst") || name.contains("orchid")) {
            return t(0.95, 0.88, 1.10, 0.92, 0.86, 1.08, 0.97, 0.93, 1.05, 0.9);
        }
        if (name.contains("dark_forest") || name.contains("spooky") || name.contains("black_forest") || name.contains("ominous")) {
            return t(0.80, 0.85, 0.80, 0.60, 0.70, 0.60, 0.85, 0.90, 0.85, 0.7);
        }
        if (is(biome, "is_jungle") || name.contains("jungle") || name.contains("rainforest") || name.contains("tropical")) {
            return t(0.88, 1.0, 0.90, 0.85, 1.0, 0.80, 0.97, 1.03, 0.95, 0.75);
        }
        if (is(biome, "is_badlands") || name.contains("badlands") || name.contains("mesa") || name.contains("canyon") || name.contains("painted")) {
            return t(1.08, 0.95, 0.85, 1.15, 0.90, 0.75, 1.06, 0.97, 0.88, 0.85);
        }
        if (is(biome, "is_desert") || name.contains("desert") || name.contains("dune") || name.contains("sand")) {
            return t(1.08, 1.02, 0.88, 1.15, 1.02, 0.82, 1.06, 1.0, 0.90, 0.85);
        }
        if (is(biome, "is_snowy") || is(biome, "is_icy") || name.contains("frozen") || name.contains("glacial") || name.contains("ice")
            || name.contains("snow") || name.contains("wintry") || name.contains("winter")) {
            return t(0.95, 1.0, 1.10, 0.95, 1.0, 1.10, 0.97, 1.0, 1.06, 0.8);
        }
        if (is(biome, "is_savanna") || name.contains("savanna") || name.contains("steppe") || name.contains("shrubland") || name.contains("brushland")) {
            return t(1.05, 1.0, 0.88, 1.08, 1.0, 0.85, 1.04, 1.0, 0.92, 0.95);
        }
        if (is(biome, "is_taiga") || name.contains("taiga") || name.contains("siberian") || name.contains("pine") || name.contains("spruce")) {
            return t(0.92, 0.97, 1.02, 0.88, 0.95, 0.95, 0.96, 0.99, 1.02, 0.9);
        }
        if (name.contains("windswept")) {
            return t(0.95, 0.97, 1.0, 0.95, 0.97, 1.0, 0.99, 1.0, 1.01, 0.9);
        }
        if (is(biome, "is_mountain") || name.contains("peak") || name.contains("highland") || name.contains("cliff") || name.contains("alpine")
            || name.contains("mountain") || name.contains("stony") || name.contains("spires") || name.contains("rocky")) {
            return t(0.98, 1.0, 1.06, 1.0, 1.0, 1.05, 1.02, 1.02, 1.04, 1);
        }
        if (name.contains("birch")) {
            return t(1.0, 1.02, 1.03, 1.0, 1.02, 1.02, 1.02, 1.02, 1.01, 1);
        }
        if (name.contains("flower") || name.contains("meadow") || name.contains("sunflower")) {
            return t(1.03, 1.0, 1.02, 1.03, 1.0, 1.0, 1.02, 1.0, 1.0, 0.95);
        }
        if (name.contains("river")) {
            return t(0.98, 1.0, 1.03, 0.95, 1.0, 1.04, 1.0, 1.0, 1.01, 1);
        }
        if (is(biome, "is_deep_ocean") || name.contains("deep_ocean")) {
            return t(0.95, 1.0, 1.05, 0.85, 0.95, 1.05, 0.97, 1.0, 1.03, 0.95);
        }
        if (is(biome, "is_ocean") || is(biome, "is_beach") || name.contains("beach") || name.contains("shore")) {
            return t(0.97, 1.0, 1.04, 0.95, 1.0, 1.04, 1.0, 1.0, 1.02, 1);
        }
        return PLAIN;
    }

    /** The eased Overworld biome tint. */
    private static Vec3 skyTint = new Vec3(1, 1, 1);
    private static Vec3 fogTint = new Vec3(1, 1, 1);

    /** The eased state: how much of the realm air (0: Overworld), and that air. */
    private static float realm;
    private static Vec3 sky = Vec3.ZERO;
    private static Vec3 fog = Vec3.ZERO;
    private static Vec3 tint = new Vec3(1, 1, 1);
    private static float ambient;
    private static float sun = 1;
    private static float clouds = 1;
    private static float fogFar = 1;
    /** Easing per client tick (20 a second): a border or biome change fades over a few seconds. */
    private static final float EASE = 0.04f;

    private static Air air(Realm realm, Biome biome) {
        Vec3 biomeFog = Vec3.fromRGB24(biome.getFogColor());
        Vec3 biomeSky = Vec3.fromRGB24(biome.getSkyColor());
        return switch (realm) {
            case NETHER -> new Air(biomeFog.scale(1.1), biomeFog, new Vec3(1.0, 0.78, 0.62), 0.07f, 0, 0, 0.55f);
            case OTHERSIDE -> new Air(biomeFog.scale(0.35), biomeFog.scale(0.5), new Vec3(0.62, 0.72, 0.84), 0.03f, 0, 0, 0.42f);
            case END -> new Air(new Vec3(0.10, 0.05, 0.14), biomeFog.scale(0.35), new Vec3(0.86, 0.74, 1.0), 0.06f, 0, 0, 0.8f);
            case TWILIGHT -> new Air(new Vec3(0.17, 0.18, 0.37).lerp(biomeSky, 0.25), biomeFog.scale(0.6), new Vec3(0.78, 0.83, 1.0), 0.03f, 0, 0.6f, 0.75f);
            case AETHER -> new Air(biomeSky, biomeFog, new Vec3(1.05, 1.05, 1.1), 0.03f, 1, 1, 1);
            default -> null;
        };
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.gameRenderer == null) {
            return;
        }
        Vec3 at = mc.gameRenderer.getMainCamera().getPosition();
        float targetRealm = 0;
        Vec3 targetSky = sky;
        Vec3 targetFog = fog;
        Vec3 targetTint = new Vec3(1, 1, 1);
        float targetAmbient = 0;
        float targetSun = 1;
        float targetClouds = 1;
        float targetFogFar = 1;
        var holder = level.getBiome(BlockPos.containing(at));
        Tint biomeTint = level.dimension() == Level.OVERWORLD ? holder.unwrapKey().map(key -> TINTS.computeIfAbsent(key, k -> tint(holder))).orElse(PLAIN) : PLAIN;
        targetTint = biomeTint.light();
        targetFogFar = biomeTint.fogFar();
        if (level.dimension() == Level.OVERWORLD && RealmLayout.known()) {
            RealmLayout.Sample sample = RealmLayout.sample(Mth.floor(at.x), Mth.floor(at.z));
            Biome biome = holder.value();
            Air first = air(sample.first(), biome);
            Air second = air(sample.second(), biome);
            double w1 = first == null ? 0 : sample.first() == sample.second() ? 1 : sample.weight();
            double w2 = second == null || sample.first() == sample.second() ? 0 : 1 - sample.weight();
            targetRealm = (float) (w1 + w2);
            if (targetRealm > 0.001) {
                Air a = first != null ? first : second;
                Air b = second != null ? second : first;
                double share = w2 / (w1 + w2);
                targetSky = a.sky().lerp(b.sky(), share);
                targetFog = a.fog().lerp(b.fog(), share);
                targetTint = biomeTint.light().lerp(a.tint().lerp(b.tint(), share), targetRealm);
                targetAmbient = (float) Mth.lerp(share, a.ambient(), b.ambient()) * targetRealm;
                targetSun = (float) Mth.lerp(targetRealm, 1, Mth.lerp(share, a.sun(), b.sun()));
                targetClouds = (float) Mth.lerp(targetRealm, 1, Mth.lerp(share, a.clouds(), b.clouds()));
                targetFogFar = (float) Mth.lerp(targetRealm, biomeTint.fogFar(), Mth.lerp(share, a.fogFar(), b.fogFar()));
            }
        }
        if (realm < 0.001 && targetRealm > 0) {
            sky = targetSky; // entering a realm from the Overworld: start from its colours, only the share eases
            fog = targetFog;
        }
        realm = Mth.lerp(EASE, realm, targetRealm);
        skyTint = skyTint.lerp(biomeTint.sky(), EASE);
        fogTint = fogTint.lerp(biomeTint.fog(), EASE);
        sky = sky.lerp(targetSky, EASE);
        fog = fog.lerp(targetFog, EASE);
        tint = tint.lerp(targetTint, EASE);
        ambient = Mth.lerp(EASE, ambient, targetAmbient);
        sun = Mth.lerp(EASE, sun, targetSun);
        clouds = Mth.lerp(EASE, clouds, targetClouds);
        fogFar = Mth.lerp(EASE, fogFar, targetFogFar);
    }

    /** The sky colour: the Overworld's, blended into the realm's (ClientLevelSkyMixin). */
    public static Vec3 sky(Vec3 overworld) {
        Vec3 tinted = clamp(overworld.multiply(skyTint));
        return realm < 0.001 ? tinted : tinted.lerp(sky, realm);
    }

    private static Vec3 clamp(Vec3 color) {
        return new Vec3(Mth.clamp(color.x, 0, 1), Mth.clamp(color.y, 0, 1), Mth.clamp(color.z, 0, 1));
    }

    /** How much of the sun, moon and stars shows (LevelRendererSkyMixin). */
    public static float sun() {
        return sun;
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float r = Mth.clamp(event.getRed() * (float) fogTint.x, 0, 1);
        float g = Mth.clamp(event.getGreen() * (float) fogTint.y, 0, 1);
        float b = Mth.clamp(event.getBlue() * (float) fogTint.z, 0, 1);
        event.setRed((float) Mth.lerp(realm, r, fog.x));
        event.setGreen((float) Mth.lerp(realm, g, fog.y));
        event.setBlue((float) Mth.lerp(realm, b, fog.z));
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || fogFar > 0.999) {
            return;
        }
        float far = event.getFarPlaneDistance() * fogFar;
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
        event.setCanceled(true); // NeoForge applies the changed distances only when cancelled
    }

    /** The Overworld's effects, with the realm's clouds, sunrise and light. */
    static final class Effects extends DimensionSpecialEffects.OverworldEffects {
        @Override
        public float getCloudHeight() {
            return clouds < 0.5f ? Float.NaN : super.getCloudHeight();
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTicks) {
            float[] color = super.getSunriseColor(timeOfDay, partialTicks);
            if (color == null || sun > 0.999f) {
                return color;
            }
            return sun < 0.05f ? null : new float[] {color[0], color[1], color[2], color[3] * sun};
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight,
                                         int pixelX, int pixelY, Vector3f colors) {
            if (realm < 0.001 && Math.abs(tint.x - 1) + Math.abs(tint.y - 1) + Math.abs(tint.z - 1) < 0.003) {
                return;
            }
            colors.set(Mth.clamp(colors.x * (float) tint.x + ambient, 0, 1), Mth.clamp(colors.y * (float) tint.y + ambient * 0.8f, 0, 1),
                Mth.clamp(colors.z * (float) tint.z + ambient * 0.7f, 0, 1));
        }
    }

    @EventBusSubscriber(modid = Expedition.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        static void register(RegisterDimensionSpecialEffectsEvent event) {
            event.register(ResourceLocation.withDefaultNamespace("overworld"), new Effects());
        }
    }
}
