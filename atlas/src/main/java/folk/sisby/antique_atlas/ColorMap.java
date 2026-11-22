package folk.sisby.antique_atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import folk.sisby.antique_atlas.util.DrawBatcher;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/**
 * Expedition: the atlas as an ordinary coloured map. Explored chunks are painted one pixel per block from Surveyor's
 * top-floor summary (block map colour, grass/foliage/water in the biome's colour, relief shading like vanilla maps,
 * darker deep water) into 512x512 textures per region of 32x32 chunks. Unexplored land is a sketch in the same colours,
 * one pixel per 4 blocks from the generator (see BiomePreview, paintRegion), in 128x128 textures, smoothed and faded.
 */
public final class ColorMap {
	static final int REGION = 32;
	private static final int DETAIL = REGION * 16;
	private static final int SKETCH_ALPHA = 0xD8;

	private final Map<Long, Layer> detail = new HashMap<>();
	private final Map<Long, Layer> sketch = new HashMap<>();
	private static int nextId;

	/** One texture of a region. */
	private static final class Layer {
		final NativeImage image;
		final DynamicTexture texture;
		final ResourceLocation id;
		boolean dirty;
		/** Drawn smoothed (the sketch: a pixel is 4 blocks); set once on the render thread. */
		boolean smooth;
		boolean filtered;

		Layer(int size) {
			image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
			texture = new DynamicTexture(image);
			id = AntiqueAtlas.id("color_map/" + nextId++);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}

		void close() {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	private static long key(int regionX, int regionZ) {
		return ChunkPos.asLong(regionX, regionZ);
	}

	private Layer layer(Map<Long, Layer> layers, int regionX, int regionZ, int size) {
		return layers.computeIfAbsent(key(regionX, regionZ), k -> new Layer(size));
	}

	/** NativeImage pixels are ABGR. */
	private static int abgr(int argb) {
		return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF);
	}

	private static int shade(int rgb, double factor) {
		int r = Math.min(255, (int) ((rgb >> 16 & 255) * factor));
		int g = Math.min(255, (int) ((rgb >> 8 & 255) * factor));
		int b = Math.min(255, (int) ((rgb & 255) * factor));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Paints an explored chunk; false if Surveyor has nothing for it yet. */
	public boolean paintChunk(WorldSummary summary, ChunkPos pos) {
		WorldTerrain terrain = summary.terrain();
		ChunkSummary chunk = terrain == null ? null : terrain.get(pos);
		LayerSummary.Raw layer = chunk == null ? null : chunk.toSingleLayer(null, null, 999);
		if (layer == null) return false;
		var biomes = terrain.getBiomePalette(pos);
		var blocks = terrain.getBlockPalette(pos);
		int regionX = Math.floorDiv(pos.x, REGION);
		int regionZ = Math.floorDiv(pos.z, REGION);
		Layer region = layer(detail, regionX, regionZ, DETAIL);
		int ox = (pos.x - regionX * REGION) * 16;
		int oz = (pos.z - regionZ * REGION) * 16;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int i = x * 16 + z;
				if (!layer.exists().get(i)) {
					region.image.setPixelRGBA(ox + x, oz + z, abgr(0xFF101010));
					continue;
				}
				int height = 999 - layer.depths()[i] + layer.waterDepths()[i];
				int north = z > 0 && layer.exists().get(i - 1) ? 999 - layer.depths()[i - 1] + layer.waterDepths()[i - 1] : height;
				Block block = blocks.byId(layer.blocks()[i]);
				Biome biome = biomes.byId(layer.biomes()[i]);
				int worldX = pos.getMinBlockX() + x;
				int worldZ = pos.getMinBlockZ() + z;
				int argb = columnColor(block, biome, layer.waterDepths()[i], height - north, worldX, worldZ);
				region.image.setPixelRGBA(ox + x, oz + z, abgr(argb));
			}
		}
		region.dirty = true;
		return true;
	}

	static int columnColor(Block block, Biome biome, int waterDepth, int rise, int x, int z) {
		if (waterDepth > 0) {
			// Vanilla map style: deeper water is darker.
			double depth = waterDepth <= 2 ? 1.0 : waterDepth <= 6 ? 0.86 : waterDepth <= 14 ? 0.74 : 0.62;
			return shade(biome.getWaterColor(), depth);
		}
		MapColor color = block.defaultMapColor();
		int rgb = color == MapColor.GRASS ? biome.getGrassColor(x, z) : color == MapColor.PLANT ? biome.getFoliageColor()
			: color == MapColor.NONE ? 0x707070 : color.col;
		return shade(rgb, rise > 0 ? 1.0 : rise == 0 ? 0.86 : 0.71);
	}

	/**
	 * Paints a region's sketch (unexplored land, BiomePreview) one pixel per 4 blocks, in the explored map's colours:
	 * water wherever the ground lies under the sea (and along rivers), deeper darker; on land the ground the biome would
	 * show - snow where it snows at that height, sand, badlands clay, mycelium, bare stone high up, foliage in forests,
	 * grass elsewhere - lit by the relief. Drawn smoothed and a little faded, under the explored detail.
	 */
	public void paintRegion(folk.sisby.surveyor.preview.BiomePreview.Region preview) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
		java.util.List<Holder<Biome>> palette = new java.util.ArrayList<>();
		for (ResourceLocation id : preview.palette()) {
			palette.add(registry.getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null));
		}
		int quarts = folk.sisby.surveyor.preview.BiomePreview.QUARTS;
		int grid = folk.sisby.surveyor.preview.BiomePreview.HEIGHT_GRID;
		java.util.List<Integer> heights = preview.heights();
		if (heights.size() < grid * grid) return;
		double quartsPerCell = folk.sisby.surveyor.preview.BiomePreview.HEIGHT_STEP * 4.0;
		double blocksPerCell = quartsPerCell * 4;
		Layer region = layer(sketch, preview.regionX(), preview.regionZ(), quarts);
		region.smooth = true;
		for (int z = 0; z < quarts; z++) {
			for (int x = 0; x < quarts; x++) {
				int index = preview.biomes()[z * quarts + x] & 0xFF;
				Holder<Biome> biome = index < palette.size() ? palette.get(index) : null;
				if (biome == null) {
					region.image.setPixelRGBA(x, z, 0);
					continue;
				}
				// The height and its slope here, bilinear on the grid (smooth across cells and regions).
				double cx = (x + 0.5) / quartsPerCell;
				double cz = (z + 0.5) / quartsPerCell;
				int i = Math.min(grid - 2, (int) cx);
				int j = Math.min(grid - 2, (int) cz);
				double u = cx - i;
				double v = cz - j;
				double h00 = heights.get(j * grid + i), h10 = heights.get(j * grid + i + 1);
				double h01 = heights.get((j + 1) * grid + i), h11 = heights.get((j + 1) * grid + i + 1);
				double height = (h00 * (1 - u) + h10 * u) * (1 - v) + (h01 * (1 - u) + h11 * u) * v;
				double dx = ((h10 - h00) * (1 - v) + (h11 - h01) * v) / blocksPerCell;
				double dz = ((h01 - h00) * (1 - u) + (h11 - h10) * u) / blocksPerCell;
				int worldX = (preview.regionX() * quarts + x) * 4 + 2;
				int worldZ = (preview.regionZ() * quarts + z) * 4 + 2;
				int argb = sketchColor(biome, height, preview.seaLevel(), dx + dz, worldX, worldZ);
				region.image.setPixelRGBA(x, z, abgr(argb & 0x00FFFFFF | SKETCH_ALPHA << 24));
			}
		}
		region.dirty = true;
	}

	static int sketchColor(Holder<Biome> holder, double height, int seaLevel, double slope, int x, int z) {
		Biome biome = holder.value();
		int depth = seaLevel - (int) Math.round(height);
		if (holder.is(BiomeTags.IS_RIVER) || depth > 0 && !holder.is(BiomeTags.IS_NETHER) && !holder.is(BiomeTags.IS_END)) {
			int water = Math.max(depth, 2);
			return shade(biome.getWaterColor(), water <= 2 ? 1.0 : water <= 6 ? 0.86 : water <= 14 ? 0.74 : 0.62);
		}
		String path = holder.unwrapKey().map(key -> key.location().getPath()).orElse("");
		int rgb;
		if (holder.is(BiomeTags.IS_NETHER)) rgb = MapColor.NETHER.col;
		else if (holder.is(BiomeTags.IS_END)) rgb = MapColor.SAND.col;
		else if (path.contains("ice_wall") || path.contains("ice_spikes") || path.contains("frozen_peaks")) rgb = MapColor.ICE.col;
		else if (biome.coldEnoughToSnow(new net.minecraft.core.BlockPos(x, (int) height, z))) rgb = MapColor.SNOW.col;
		else if (holder.is(BiomeTags.IS_BADLANDS)) rgb = MapColor.COLOR_ORANGE.col;
		else if (holder.is(BiomeTags.IS_BEACH) || path.contains("desert") || path.contains("dune")) rgb = MapColor.SAND.col;
		else if (path.contains("mushroom")) rgb = MapColor.COLOR_PURPLE.col;
		else if (height > seaLevel + 110 || path.contains("stony") || path.contains("peak")) rgb = MapColor.STONE.col;
		else if (holder.is(BiomeTags.IS_FOREST) || holder.is(BiomeTags.IS_JUNGLE) || holder.is(BiomeTags.IS_TAIGA)) rgb = biome.getFoliageColor();
		else rgb = biome.getGrassColor(x, z);
		// Lit from the north-west, like the explored map's relief.
		return shade(rgb, Math.max(0.72, Math.min(1.12, 0.93 - 2.2 * slope)));
	}

	/**
	 * Draws the regions overlapping the map rectangle (screen coordinates). worldToScreen: blocks to screen pixels;
	 * originX/Z: the world block at screen (0, 0).
	 */
	public void render(PoseStack matrices, MultiBufferSource buffers, int light, double screenX0, double screenZ0, double pixelsPerBlock,
	                   float mapX, float mapY, float mapWidth, float mapHeight) {
		double blockX0 = (mapX - screenX0) / pixelsPerBlock;
		double blockZ0 = (mapY - screenZ0) / pixelsPerBlock;
		double blockX1 = (mapX + mapWidth - screenX0) / pixelsPerBlock;
		double blockZ1 = (mapY + mapHeight - screenZ0) / pixelsPerBlock;
		int rx0 = Math.floorDiv((int) Math.floor(blockX0), DETAIL);
		int rz0 = Math.floorDiv((int) Math.floor(blockZ0), DETAIL);
		int rx1 = Math.floorDiv((int) Math.floor(blockX1), DETAIL);
		int rz1 = Math.floorDiv((int) Math.floor(blockZ1), DETAIL);
		for (Map<Long, Layer> layers : java.util.List.of(sketch, detail)) {
			for (int rz = rz0; rz <= rz1; rz++) {
				for (int rx = rx0; rx <= rx1; rx++) {
					Layer region = layers.get(key(rx, rz));
					if (region == null) continue;
					if (region.dirty) {
						region.texture.upload();
						region.dirty = false;
					}
					if (region.smooth && !region.filtered) {
						region.texture.setFilter(true, false);
						region.filtered = true;
					}
					// The region's block rectangle, clipped to the map.
					double bx0 = Math.max(rx * DETAIL, blockX0);
					double bz0 = Math.max(rz * DETAIL, blockZ0);
					double bx1 = Math.min((rx + 1) * DETAIL, blockX1);
					double bz1 = Math.min((rz + 1) * DETAIL, blockZ1);
					if (bx1 <= bx0 || bz1 <= bz0) continue;
					try (DrawBatcher batcher = new DrawBatcher(matrices, buffers, region.id, 1, 1, light, true)) {
						batcher.addExact((float) (screenX0 + bx0 * pixelsPerBlock), (float) (screenX0 + bx1 * pixelsPerBlock),
							(float) (screenZ0 + bz0 * pixelsPerBlock), (float) (screenZ0 + bz1 * pixelsPerBlock), 0,
							(float) ((bx0 - rx * DETAIL) / DETAIL), (float) ((bx1 - rx * DETAIL) / DETAIL),
							(float) ((bz0 - rz * DETAIL) / DETAIL), (float) ((bz1 - rz * DETAIL) / DETAIL), 0xFFFFFFFF);
					}
				}
			}
		}
	}

	public void close() {
		detail.values().forEach(Layer::close);
		sketch.values().forEach(Layer::close);
		detail.clear();
		sketch.clear();
	}
}
