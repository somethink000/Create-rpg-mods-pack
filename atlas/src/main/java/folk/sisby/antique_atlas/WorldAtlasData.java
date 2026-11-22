package folk.sisby.antique_atlas;

import com.google.common.collect.Multimap;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.reloader.StructureTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import folk.sisby.antique_atlas.util.Rect;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.Pair;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public class WorldAtlasData {
	public static final Map<ResourceKey<Level>, WorldAtlasData> WORLDS = new HashMap<>();

	public static WorldAtlasData getOrCreate(ResourceKey<Level> dimension) {
		return WORLDS.computeIfAbsent(dimension, WorldAtlasData::new);
	}

	protected final ResourceKey<Level> dimension;

	public WorldAtlasData(ResourceKey<Level> dimension) {
		this.dimension = dimension;
	}

	// Expedition: the sketch of unexplored land, read from the world generator (see BiomePreview).
	protected final Map<ChunkPos, ResourceLocation> previewBiomes = new ConcurrentHashMap<>();
	protected final Map<ChunkPos, TileTexture> previewTiles = new ConcurrentHashMap<>();
	/** Regions asked for: when (ms), or Long.MAX_VALUE once answered. The server drops requests when busy: ask again. */
	protected final Map<Long, Long> requestedRegions = new ConcurrentHashMap<>();
	private static final long RETRY_MS = 10_000;
	/** New region requests per frame, so a fast scroll doesn't flood the server. */
	private static final int REQUESTS_PER_CALL = 6;

	protected final ColorMap colorMap = new ColorMap();
	/** Chunks painted from explored terrain. */
	protected final Set<ChunkPos> explored = new HashSet<>();

	public ColorMap colorMap() {
		return colorMap;
	}

	public void close() {
		colorMap.close();
	}

	public void onPreview(folk.sisby.surveyor.preview.BiomePreview.Region region) {
		int size = folk.sisby.surveyor.preview.BiomePreview.REGION_CHUNKS;
		int quarts = folk.sisby.surveyor.preview.BiomePreview.QUARTS;
		requestedRegions.put(ChunkPos.asLong(region.regionX(), region.regionZ()), Long.MAX_VALUE);
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				// A chunk's biome: the sample at its middle.
				int index = region.biomes()[(z * 4 + 2) * quarts + x * 4 + 2] & 0xFF;
				if (index < region.palette().size()) {
					ChunkPos pos = new ChunkPos(region.regionX() * size + x, region.regionZ() * size + z);
					previewBiomes.put(pos, region.palette().get(index));
					previewTiles.remove(pos);
				}
			}
		}
		colorMap.paintRegion(region);
	}

	/** Asks the server for the sketch of the regions in this chunk rectangle not asked for yet (current dimension only). */
	public void requestPreview(Rect chunks) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.level().dimension() != dimension || mc.getConnection() == null) return;
		int size = folk.sisby.surveyor.preview.BiomePreview.REGION_CHUNKS;
		int sent = 0;
		long now = System.currentTimeMillis();
		for (int rz = Math.floorDiv(chunks.minY, size); rz <= Math.floorDiv(chunks.maxY, size); rz++) {
			for (int rx = Math.floorDiv(chunks.minX, size); rx <= Math.floorDiv(chunks.maxX, size); rx++) {
				long key = ChunkPos.asLong(rx, rz);
				Long asked = requestedRegions.get(key);
				if (sent < REQUESTS_PER_CALL && (asked == null || asked != Long.MAX_VALUE && now - asked > RETRY_MS)) {
					requestedRegions.put(key, now);
					net.neoforged.neoforge.network.PacketDistributor.sendToServer(new folk.sisby.surveyor.preview.BiomePreview.Request(rx, rz));
					sent++;
				}
			}
		}
	}

	/** True where the map shows only the sketch (not explored yet). */
	public boolean isPreview(int x, int z) {
		ChunkPos pos = new ChunkPos(x, z);
		return !explored.contains(pos) && previewBiomes.containsKey(pos);
	}

	/** The biome at a chunk as far as the map knows it (the generator's, for explored and unexplored land). */
	public ResourceLocation biomeAt(ChunkPos pos) {
		ResourceLocation biome = previewBiomes.get(pos);
		if (biome != null) return biome;
		return debugBiomes.containsKey(pos) ? debugBiomes.get(pos).id() : null;
	}

	private TileTexture previewTile(ChunkPos pos) {
		ResourceLocation biome = previewBiomes.get(pos);
		if (biome == null || !BiomeTileProviders.getInstance().hasFallbacks()) return null;
		return previewTiles.computeIfAbsent(pos, p -> BiomeTileProviders.getInstance().getTileProvider(biome).getTexture(p, null));
	}

	public static boolean isEmpty(ResourceKey<Level> dimension) {
		return !WORLDS.containsKey(dimension) || WORLDS.get(dimension).isEmpty();
	}

	protected final Map<ChunkPos, TileTexture> biomeTiles = new HashMap<>();
	protected final Map<ChunkPos, TileTexture> structureTiles = new HashMap<>();
	protected final Map<UUID, Map<ResourceLocation, Pair<Landmark, MarkerTexture>>> landmarkMarkers = new ConcurrentHashMap<>();
	protected final Map<Landmark, MarkerTexture> structureMarkers = new ConcurrentHashMap<>();

	protected final Rect tileScope = new Rect(0, 0, 0, 0);
	protected final Set<ChunkPos> terrainDequeHash = new HashSet<>();
	protected final Deque<ChunkPos> terrainDeque = new ConcurrentLinkedDeque<>();
	protected boolean isFinished = false;

	// Debug Display Info
	protected final Map<ChunkPos, String> debugBiomePredicates = new HashMap<>();
	protected final Map<ChunkPos, String> debugStructurePredicates = new HashMap<>();
	protected final Map<ChunkPos, TerrainTileProvider> debugBiomes = new HashMap<>();
	protected final Map<ChunkPos, StructureTileProvider> debugStructures = new HashMap<>();


	private boolean isEmpty() {
		return tileScope.maxY == tileScope.minY && terrainDequeHash.isEmpty();
	}

	public void onTerrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
		for (ChunkPos pos : RegionPos.regionsToChunks(chunks)) {
			if (!terrainDequeHash.contains(pos)) { // re-painted when explored terrain changes
				terrainDequeHash.add(pos);
				terrainDeque.add(pos);
			}
		}
	}

	public void onStructuresAdded(WorldSummary summary, Multimap<ResourceKey<Structure>, ChunkPos> starts) {
		starts.forEach((key, pos) -> StructureTileProviders.getInstance().resolve(structureTiles, debugStructures, debugStructurePredicates, structureMarkers, summary, key, pos, summary.structures().get(key, pos), summary.structures().getType(key), summary.structures().getTags(key)));
	}

	public void tick(WorldSummary summary) {
		if (!BiomeTileProviders.getInstance().hasFallbacks()) return;
		// Painting a chunk is cheap: many per tick.
		for (int i = 0; i < Math.max(AntiqueAtlas.CONFIG.chunkTickLimit, 256); i++) {
			ChunkPos pos = terrainDeque.pollFirst();
			terrainDequeHash.remove(pos);
			if (pos == null) break;
			if (colorMap.paintChunk(summary, pos)) {
				explored.add(pos);
				tileScope.extendTo(pos.x, pos.z);
			}
		}
		if (!isFinished && terrainDeque.isEmpty()) {
			isFinished = true;
			AntiqueAtlas.LOGGER.info("[Antique Atlas] Finished loading terrain for {} - {} tiles.", summary.dimension(), explored.size());
		}
	}

	public Rect getScope() {
		return tileScope;
	}

	public TileTexture getTile(int x, int z) {
		return getTile(new ChunkPos(x, z));
	}

	public TileTexture getTile(ChunkPos pos) {
		if (!biomeTiles.containsKey(pos)) {
			TileTexture preview = previewTile(pos);
			if (preview != null) return preview;
			return AntiqueAtlas.CONFIG.emptyHandling == AntiqueAtlasConfig.EmptyHandling.CLOUDS ? TileTextures.getInstance().getTextures().get(AntiqueAtlas.id("clouds")) : null;
		}
		return structureTiles.getOrDefault(pos, biomeTiles.get(pos));
	}

	public ResourceLocation getProvider(ChunkPos pos) {
		if (structureTiles.containsKey(pos)) {
			return debugStructures.get(pos).id();
		} else {
			return debugBiomes.containsKey(pos) ? debugBiomes.get(pos).id() : null;
		}
	}

	public String getTilePredicate(ChunkPos pos) {
		if (structureTiles.containsKey(pos)) {
			return debugStructurePredicates.get(pos);
		} else {
			return debugBiomePredicates.get(pos);
		}
	}

	public void addLandmarkMarker(Landmark landmark, MarkerTexture texture) {
		landmarkMarkers.computeIfAbsent(landmark.owner(), t -> new ConcurrentHashMap<>()).put(landmark.id(), Pair.of(landmark, texture));
	}

	public static Landmark copyLandmarkWith(Landmark landmark, ResourceLocation id, Consumer<LandmarkComponentMap> modifier) {
		LandmarkComponentMap copy = LandmarkComponentMap.builder().build();
		landmark.components().keySet().forEach(t -> copy.set(t, landmark.components().get(t)));
		modifier.accept(copy);
		return new Landmark(landmark.owner(), id, copy);
	}

	public void addLandmark(Landmark landmark) {
		if (landmark == null) return;
		if (landmark.id().getPath().startsWith("grave")) {
			AntiqueAtlasConfig.GraveStyle style = AntiqueAtlas.CONFIG.graveStyle;
			Component name = landmark.get(LandmarkComponentTypes.NAME);
			if (name == null && style == AntiqueAtlasConfig.GraveStyle.CAUSE) style = AntiqueAtlasConfig.GraveStyle.DIED;
			MutableComponent timeText = Component.literal(String.valueOf(1 + (landmark.getOrDefault(LandmarkComponentTypes.TIME, 0L) / 24000L))).withStyle(ChatFormatting.WHITE);
			String key = "gui.antique_atlas.marker.death.%s".formatted(style.toString().toLowerCase());
			MutableComponent text = switch (style) {
				case CAUSE -> Component.translatable(key, name.copy().withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.RED), timeText).withStyle(ChatFormatting.GRAY);
				case GRAVE, ITEMS, DIED -> Component.translatable(key, Component.translatable("gui.antique_atlas.marker.death.%s.verb".formatted(style.toString().toLowerCase())).withStyle(ChatFormatting.RED), timeText).withStyle(ChatFormatting.GRAY);
				case EUPHEMISMS -> Component.translatable(key, Component.translatable("gui.antique_atlas.marker.death.%s.verb.%s".formatted(style.toString().toLowerCase(), new Random(landmark.getOrDefault(LandmarkComponentTypes.SEED, 0)).nextInt(11))).withStyle(ChatFormatting.RED), timeText).withStyle(ChatFormatting.GRAY);
			};
			addLandmarkMarker(copyLandmarkWith(landmark, landmark.id(), m -> {
				m.set(LandmarkComponentTypes.COLOR, DyeColor.GRAY.getTextureDiffuseColor());
				m.set(LandmarkComponentTypes.NAME, text);
			}), MarkerTextures.getInstance().fromLandmark(landmark, style == AntiqueAtlasConfig.GraveStyle.ITEMS ? "items" : null));
		} else {
			addLandmarkMarker(landmark, MarkerTextures.getInstance().fromLandmark(landmark));
		}
	}

	public void onLandmarksAdded(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
		landmarks.forEach((type, pos) -> this.addLandmark(summary.landmarks().get(type, pos)));
		if (Minecraft.getInstance().screen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public void onLandmarksRemoved(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
		landmarks.forEach((type, pos) -> {
			if (landmarkMarkers.containsKey(type)) {
				landmarkMarkers.get(type).remove(pos);
				if (landmarkMarkers.get(type).isEmpty()) landmarkMarkers.remove(type);
			}
		});
		if (Minecraft.getInstance().screen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public boolean deleteLandmark(ResourceKey<Level> dimension, Landmark landmark) {
		WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
		if (summary == null || summary.landmarks() == null || landmark.owner().equals(WorldLandmarks.GLOBAL) || !SurveyorClient.canModify(landmark.owner())) return false;
		summary.landmarks().remove(landmark.owner(), landmark.id());
		return true;
	}

	public Map<Landmark, MarkerTexture> getEditableLandmarks() {
		Map<Landmark, MarkerTexture> map = new HashMap<>();
		landmarkMarkers.forEach((type, landmarks) -> landmarks.forEach((pos, pair) -> { // Don't allow editing global landmarks via GUI.
			if (!pair.left().owner().equals(WorldLandmarks.GLOBAL) && SurveyorClient.canModify(pair.left().owner())) map.put(pair.left(), pair.right());
		}));
		return map;
	}

	public Map<Landmark, MarkerTexture> getAllMarkers(int tileChunks) {
		Map<Landmark, MarkerTexture> map = new HashMap<>();
		landmarkMarkers.forEach((type, landmarks) -> landmarks.forEach((pos, pair) -> map.put(pair.left(), pair.right())));
		structureMarkers.forEach((landmark, texture) -> {
			if (tileChunks >= texture.nearClip() && tileChunks <= texture.farClip()) map.put(landmark, texture);
		});
		return map;
	}

	public MarkerTexture getMarkerTexture(Landmark landmark) {
		return landmarkMarkers.containsKey(landmark.owner()) && landmarkMarkers.get(landmark.owner()).containsKey(landmark.id()) ? landmarkMarkers.get(landmark.owner()).get(landmark.id()).right() : structureMarkers.get(landmark);
	}

	public boolean isLoading() {
		return terrainDequeHash.size() > 20;
	}
}
