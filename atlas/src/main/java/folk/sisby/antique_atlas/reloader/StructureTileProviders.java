package folk.sisby.antique_atlas.reloader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.StructureTileProvider;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.structure.JigsawPieceSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureStartSummary;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static folk.sisby.antique_atlas.reloader.BiomeTileProviders.resolveTextureJson;

public class StructureTileProviders extends SimpleJsonResourceReloadListener {
	public static final StructureTileProviders INSTANCE = new StructureTileProviders();

	public static final ResourceLocation ID = AntiqueAtlas.id("structures");

	public static StructureTileProviders getInstance() {
		return INSTANCE;
	}

	protected final Map<ResourceLocation, StructureTileProvider> startTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> typeTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> tagTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceTypeTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceJigsawSingleTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceJigsawFeatureTiles = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> startMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> typeMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> tagMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceTypeMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceJigsawSingleMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceJigsawFeatureMarkers = new HashMap<>();

	public enum ProviderType {
		START("start"),
		TAG("tag"),
		TYPE("type"),
		PIECE_TYPE("piece/type"),
		JIGSAW_SINGLE("piece/jigsaw/single"),
		JIGSAW_FEATURE("piece/jigsaw/feature");

		public final String key;

		ProviderType(String key) {
			this.key = key;
		}

		public String prefix() {
			return key + "/";
		}

		public String translation(ResourceLocation id) {
			return "structure.%s.%s".formatted(key.replace('/', '.'), id.toString().replace(':', '.'));
		}
	}

	protected final Map<ProviderType, Pair<Map<ResourceLocation, StructureTileProvider>, Map<ResourceLocation, MarkerTexture>>> PROVIDER_MAPS = Map.of(
		ProviderType.START, Pair.of(startTiles, startMarkers),
		ProviderType.TYPE, Pair.of(typeTiles, typeMarkers),
		ProviderType.TAG, Pair.of(tagTiles, tagMarkers),
		ProviderType.PIECE_TYPE, Pair.of(pieceTypeTiles, pieceTypeMarkers),
		ProviderType.JIGSAW_SINGLE, Pair.of(pieceJigsawSingleTiles, pieceJigsawSingleMarkers),
		ProviderType.JIGSAW_FEATURE, Pair.of(pieceJigsawFeatureTiles, pieceJigsawFeatureMarkers)
	);

	public StructureTileProviders() {
		super(new Gson(), "atlas/structure");
	}

	public void resolve(Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders, Map<ChunkPos, String> tilePredicates, StructurePieceSummary piece, WorldSummary summary) {
		if (piece instanceof JigsawPieceSummary jigsawPiece) {
			if (pieceJigsawSingleTiles.containsKey(jigsawPiece.getId())) {
				StructureTileProvider provider = (jigsawPiece.getElementType() == StructurePoolElementType.FEATURE ? pieceJigsawFeatureTiles : pieceJigsawSingleTiles).get(jigsawPiece.getId());
				provider.getTextures(summary, jigsawPiece.getBoundingBox(), jigsawPiece.getJunctions(), tilePredicates).forEach((pos, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos, texture);
					structureProviders.put(pos, provider);
				});
			}
		} else {
			ResourceLocation pieceTypeId = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
			if (pieceTypeTiles.containsKey(pieceTypeId)) {
				StructureTileProvider provider = pieceTypeTiles.get(pieceTypeId);
				provider.getTextures(summary, piece.getBoundingBox(), tilePredicates).forEach((pos, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos, texture);
					structureProviders.put(pos, provider);
				});
			}
		}
	}

	public void resolve(Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders, Map<ChunkPos, String> debugPredicates, Map<Landmark, MarkerTexture> outMarkers, WorldSummary summary, ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary start, ResourceKey<StructureType<?>> type, Collection<TagKey<Structure>> tags) {
		if (startMarkers.containsKey(key.location())) {
			MarkerTexture texture = startMarkers.get(key.location());
			outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.location().withPath(p -> "start/" + p + "/" + pos.x + "/" + pos.z), b -> b
				.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
				.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.START.translation(key.location())))
			), texture);
		} else if (type != null && typeMarkers.containsKey(type.location())) {
			MarkerTexture texture = typeMarkers.get(type.location());
			outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.location().withPath(p -> "start/" + p + "/" + pos.x + "/" + pos.z), b -> b
				.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
				.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.TYPE.translation(type.location())))
			), texture);
		} else {
			var tagged = tagMarkers.entrySet().stream().filter(entry -> tags.contains(TagKey.create(Registries.STRUCTURE, entry.getKey()))).findFirst();
			tagged.ifPresent(entry ->
				outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.location().withPath(p -> "start/" + p + "/" + pos.x + "/" + pos.z), b -> b
					.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
					.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.TAG.translation(entry.getKey())))
				), entry.getValue()));
			MarkerTexture fallback = tagged.isEmpty() ? fallbackMarker(key.location()) : null;
			if (fallback != null) {
				outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.location().withPath(p -> "start/" + p + "/" + pos.x + "/" + pos.z), b -> b
					.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
					.add(LandmarkComponentTypes.NAME, readableName(key.location()))
				), fallback);
			}
		}

		if (startTiles.containsKey(key.location())) {
			StructureTileProvider provider = startTiles.get(key.location());
			provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
				if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
				outTiles.put(pos2, texture);
				structureProviders.put(pos2, provider);
			});
		} else if (type != null && typeTiles.containsKey(type.location())) {
			StructureTileProvider provider = typeTiles.get(key.location());
			provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
				if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
				outTiles.put(pos2, texture);
				structureProviders.put(pos2, provider);
			});
		} else {
			tags.stream().filter(t -> tagTiles.containsKey(t.location())).findFirst().ifPresent(tag -> {
				StructureTileProvider provider = tagTiles.get(tag.location());
				provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos2, texture);
					structureProviders.put(pos2, provider);
				});
			});
		}

		start.getChildren().forEach(p -> resolve(outTiles, structureProviders, debugPredicates, p, summary));
	}

	/** Expedition: modded structures have no marker files; pick one by what the structure's name says it is. */
	private static final List<Map.Entry<java.util.regex.Pattern, String>> FALLBACK_MARKERS = List.of(
		Map.entry(java.util.regex.Pattern.compile("tree|river|rock|boulder|bush|flower|log|well|lamp|fountain|statue|grave_?stone|pond|spring|geode|crystal_cluster|floating_island|cloud"), ""),
		Map.entry(java.util.regex.Pattern.compile("village|town|hamlet|settlement|camp(?!aign)|outpost_house"), "structure/plains_village"),
		Map.entry(java.util.regex.Pattern.compile("tower|spire|lighthouse|obelisk"), "custom/tower"),
		Map.entry(java.util.regex.Pattern.compile("ship|boat|wreck|galleon|junk|whale|airship"), "custom/scroll"),
		Map.entry(java.util.regex.Pattern.compile("mine|quarry|foundry|factory|forge|smith|workshop|mining"), "custom/pickaxe"),
		Map.entry(java.util.regex.Pattern.compile("ruin|remnant|ruined|abandoned|broken|lost"), "custom/stuck_sword"),
		Map.entry(java.util.regex.Pattern.compile("dungeon|crypt|catacomb|tomb|grave|prison|fortress|stronghold|castle|citadel|keep|temple|pyramid|monument|lair|arena|nest|den|sanctum|chamber|vault|labyrinth|maze|bastion|mansion|outpost|hideout|fort|tunnel"), "custom/skull"),
		Map.entry(java.util.regex.Pattern.compile(".*"), "custom/point"));

	static MarkerTexture fallbackMarker(ResourceLocation structure) {
		String path = structure.getPath();
		for (Map.Entry<java.util.regex.Pattern, String> entry : FALLBACK_MARKERS) {
			if (entry.getKey().matcher(path).find()) {
				return entry.getValue().isEmpty() ? null : MarkerTextures.getInstance().asMap().get(AntiqueAtlas.id(entry.getValue()));
			}
		}
		return null;
	}

	static Component readableName(ResourceLocation structure) {
		String key = "structure." + structure.getNamespace() + "." + structure.getPath().replace('/', '.');
		String path = structure.getPath().substring(structure.getPath().lastIndexOf('/') + 1).replace('_', ' ');
		return Component.translatableWithFallback(key, Character.toUpperCase(path.charAt(0)) + path.substring(1));
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
		AntiqueAtlas.LOGGER.info("[Antique Atlas] Reloading Structure Tile / Marker Providers...");
		Map<ResourceLocation, TileTexture> textures = TileTextures.getInstance().getTextures();
		Set<TileTexture> unusedTextures = new HashSet<>(textures.values().stream().filter(t -> t.id().getPath().startsWith("structure")).toList());

		PROVIDER_MAPS.values().forEach(p -> p.left().clear());
		PROVIDER_MAPS.values().forEach(p -> p.right().clear());
		for (Map.Entry<ResourceLocation, JsonElement> fileEntry : prepared.entrySet()) {
			ResourceLocation fileId = fileEntry.getKey();
			PROVIDER_MAPS.forEach((providerType, pair) -> {
				Map<ResourceLocation, StructureTileProvider> providerMap = pair.left();
				Map<ResourceLocation, MarkerTexture> markerMap = pair.right();
				if (fileId.getPath().startsWith(providerType.prefix())) {
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), fileId.getPath().substring(providerType.prefix().length()));
					try {
						JsonObject fileJson = fileEntry.getValue().getAsJsonObject();
						if (fileJson.has("textures")) {
							JsonElement textureJson = fileJson.get("textures");
							int priority = fileJson.has("priority") ? fileJson.get("priority").getAsInt() : 999;
							List<TileTexture> defaultTextures = resolveTextureJson(textures, textureJson);
							if (defaultTextures != null) {
								StructureTileProvider provider = new StructureTileProvider(id, priority, defaultTextures);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							} else {
								JsonObject textureObject = textureJson.getAsJsonObject();
								Map<StructureTileProvider.ChunkMatcher, List<TileTexture>> matchers = new HashMap<>();
								for (String matcherKey : textureObject.keySet()) {
									ResourceLocation matcherId = AntiqueAtlas.id(matcherKey);
									StructureTileProvider.ChunkMatcher matcher = StructureTileProvider.getChunkMatcher(matcherId);
									if (matcher == null) throw new IllegalStateException("Matcher %s does not exist!".formatted(matcherId.toString()));
									List<TileTexture> matcherTextures = resolveTextureJson(textures, textureObject.get(matcherKey));
									if (matcherTextures == null) throw new IllegalStateException("Malformed object %s in textures object!".formatted(matcherId.toString()));
									matcherTextures.forEach(unusedTextures::remove);
									matchers.put(matcher, matcherTextures);
								}
								if (matchers.isEmpty()) {
									throw new IllegalStateException("No matcher keys were found in the textures object!");
								}
								StructureTileProvider provider = new StructureTileProvider(id, priority, matchers);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							}
						}
						if (fileJson.has("markers")) {
							JsonElement markerJson = fileJson.get("markers");
							ResourceLocation markerTextureId = ResourceLocation.tryParse(markerJson.getAsString());
							MarkerTexture texture = MarkerTextures.getInstance().asMap().get(markerTextureId);
							if (texture == null) throw new IllegalStateException("Marker texture %s does not exist!".formatted(markerTextureId));
							AntiqueAtlas.CONFIG.structureMarkers.putIfAbsent(fileId.toString(), true);
							if (AntiqueAtlas.CONFIG.structureMarkers.get(fileId.toString())) {
								markerMap.put(id, texture);
							}
						}
					} catch (Exception e) {
						AntiqueAtlas.LOGGER.error("[Antique Atlas] Error reading structure tile provider {}!", fileId, e);
					}
				}
			});
		}

		for (TileTexture texture : unusedTextures) {
			AntiqueAtlas.LOGGER.warn("[Antique Atlas] Tile texture {} isn't referenced by any structure tile provider!", texture.displayId());
		}
	}


}
