package folk.sisby.antique_atlas;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.gui.MinimapRenderer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import folk.sisby.antique_atlas.gui.core.ScreenState;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.reloader.StructureTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.Map;
import java.util.UUID;

@Mod(value = AntiqueAtlas.ID, dist = Dist.CLIENT)
public class AntiqueAtlas {
	public static final String ID = "antique_atlas";
	public static final String NAME = "Antique Atlas";

	public static final Logger LOGGER = LogManager.getLogger(NAME);

	public static final AntiqueAtlasConfig CONFIG = AntiqueAtlasConfig.createToml(FMLPaths.CONFIGDIR.get(), "", "antique-atlas", AntiqueAtlasConfig.class);
	public static final ScreenState<AtlasScreen> lastState = new ScreenState<>();

	/** Bookmarks other mods add to the atlas' side (the Expedition route tab). */
	public record ExtraBookmark(Component title, ResourceLocation icon, int iconWidth, int iconHeight, int tint, Runnable onClick) {
	}

	public static final List<ExtraBookmark> EXTRA_BOOKMARKS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static ResourceLocation id(String path) {
		return path.contains(":") ? ResourceLocation.tryParse(path) : ResourceLocation.fromNamespaceAndPath(ID, path);
	}

	public static AtlasScreen openAtlasScreen() {
		if (Minecraft.getInstance().screen == null) {
			AtlasScreen screen = new AtlasScreen();
			screen.init();
			screen.prepareToOpen();
			screen.tick();
			Minecraft.getInstance().setScreen(screen);
			return screen;
		}
		return null;
	}

	public static Map<UUID, PlayerSummary> getOrderedFriends() {
		Map<UUID, PlayerSummary> friends = SurveyorClient.getFriends();
		PlayerSummary playerSummary = friends.remove(SurveyorClient.getClientUuid());
		Map<UUID, PlayerSummary> orderedFriends = new LinkedHashMap<>(friends);
		if (playerSummary != null) orderedFriends.put(SurveyorClient.getClientUuid(), playerSummary);
		return orderedFriends;
	}

	public AntiqueAtlas(IEventBus modBus) {
		AntiqueAtlasKeybindings.init(modBus);
		modBus.addListener((RegisterClientReloadListenersEvent e) -> {
			// Textures first: the tile providers resolve them (Fabric listener dependencies before).
			e.registerReloadListener(TileTextures.getInstance());
			e.registerReloadListener(MarkerTextures.getInstance());
			e.registerReloadListener(StructureTileProviders.getInstance());
			e.registerReloadListener(BiomeTileProviders.getInstance());
		});
		// Expedition: no atlas item — every player has the HUD minimap, M opens the full atlas.
		modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelow(VanillaGuiLayers.DEBUG_OVERLAY, id("minimap"), MinimapRenderer::render));

		SurveyorClientEvents.Register.terrainUpdated(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s.dimension()).onTerrainUpdated(s, k));
		SurveyorClientEvents.Register.structuresAdded(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s.dimension()).onStructuresAdded(s, k));
		SurveyorClientEvents.Register.landmarksAdded(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s.dimension()).onLandmarksAdded(s, k));
		SurveyorClientEvents.Register.landmarksRemoved(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s.dimension()).onLandmarksRemoved(s, k));
		NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
			if (e.getLevel().isClientSide() && Minecraft.getInstance().getConnection() != null)
				SurveyorClient.getSummaries(Minecraft.getInstance().getConnection()).values().forEach(s -> WorldAtlasData.getOrCreate(s.dimension()).tick(s));
		});
		NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> BiomeTileProviders.getInstance().registerFallbacks(e.getRegistryAccess().registryOrThrow(Registries.BIOME)));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
			BiomeTileProviders.getInstance().clearFallbacks();
			WorldAtlasData.WORLDS.values().forEach(WorldAtlasData::close);
			WorldAtlasData.WORLDS.clear();
		});

		folk.sisby.surveyor.preview.BiomePreview.CLIENT = region -> WorldAtlasData.getOrCreate(region.dimension()).onPreview(region);
		WorldSummary.enableTerrain();
		WorldSummary.enableStructures();
		WorldSummary.enableLandmarks();
	}
}
