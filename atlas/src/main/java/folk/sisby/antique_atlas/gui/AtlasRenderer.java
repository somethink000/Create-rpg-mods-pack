package folk.sisby.antique_atlas.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.antique_atlas.gui.core.ScreenState;
import folk.sisby.antique_atlas.gui.tiles.SubTile;
import folk.sisby.antique_atlas.gui.tiles.SubTileQuartet;
import folk.sisby.antique_atlas.gui.tiles.TileRenderIterator;
import folk.sisby.antique_atlas.util.ColorUtil;
import folk.sisby.antique_atlas.util.DrawBatcher;
import folk.sisby.antique_atlas.util.DrawUtil;
import folk.sisby.antique_atlas.util.MathUtil;
import folk.sisby.antique_atlas.util.Rect;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public interface AtlasRenderer {
	Map<ResourceLocation, AtlasOverlay> overlays = new HashMap<>();

	static void registerOverlay(ResourceLocation id, AtlasOverlay overlay) {
		overlays.put(id, overlay);
	}

	ResourceLocation BOOK = AntiqueAtlas.id("textures/gui/book.png");
	ResourceLocation BOOK_FULLSCREEN = AntiqueAtlas.id("book_fullscreen");
	ResourceLocation BOOK_FULLSCREEN_M = AntiqueAtlas.id("middle/book_fullscreen_m");
	ResourceLocation BOOK_FULLSCREEN_R = AntiqueAtlas.id("book_fullscreen_r");
	ResourceLocation BOOK_FRAME = AntiqueAtlas.id("textures/gui/book_frame.png");
	ResourceLocation BOOK_FRAME_FULLSCREEN = AntiqueAtlas.id("book_frame_fullscreen");
	ResourceLocation BOOK_FRAME_FULLSCREEN_M = AntiqueAtlas.id("middle/book_frame_fullscreen_m");
	ResourceLocation BOOK_FRAME_FULLSCREEN_R = AntiqueAtlas.id("book_frame_fullscreen_r");
	ResourceLocation BOOK_FRAME_NARROW = AntiqueAtlas.id("textures/gui/book_frame_narrow.png");
	ResourceLocation BOOK_FRAME_NARROW_FULLSCREEN = AntiqueAtlas.id("book_frame_narrow_fullscreen");
	ResourceLocation BOOK_FRAME_NARROW_FULLSCREEN_M = AntiqueAtlas.id("middle/book_frame_narrow_fullscreen_m");
	ResourceLocation BOOK_FRAME_NARROW_FULLSCREEN_R = AntiqueAtlas.id("book_frame_narrow_fullscreen_r");
	ResourceLocation PLAYER = AntiqueAtlas.id("textures/gui/player.png");
	ResourceLocation ERASER = AntiqueAtlas.id("textures/gui/eraser.png");
	ResourceLocation ICON_ADD_MARKER = AntiqueAtlas.id("textures/gui/icons/add_marker.png");
	ResourceLocation ICON_DELETE_MARKER = AntiqueAtlas.id("textures/gui/icons/del_marker.png");
	ResourceLocation ICON_SHOW_MARKERS = AntiqueAtlas.id("textures/gui/icons/show_markers.png");
	ResourceLocation ICON_HIDE_MARKERS = AntiqueAtlas.id("textures/gui/icons/hide_markers.png");
	ResourceLocation ICON_UNKNOWN = AntiqueAtlas.id("textures/gui/icons/unknown.png");
	Component TEXT_ADD_MARKER = Component.translatable("gui.antique_atlas.addMarker");
	Component TEXT_ADD_MARKER_HERE = Component.translatable("gui.antique_atlas.addMarkerHere");

	int DEFAULT_BOOK_WIDTH = 310;
	int DEFAULT_BOOK_HEIGHT = 218;
	int MAP_BORDER_WIDTH = 17;
	int MAP_BORDER_HEIGHT = 11;
	float PLAYER_ROTATION_STEPS = 16;
	int PLAYER_ICON_WIDTH = 7;
	int PLAYER_ICON_HEIGHT = 8;
	int BOOKMARK_SPACING = 2;
	int MARKER_SIZE = 32;
	int NAVIGATE_STEP = 24; // How much the map view is offset, in blocks, per click (or per tick).
	int MAX_LIGHT = 0xF000F0;

	ScreenState.State<AtlasScreen> NORMAL = new ScreenState.ToggleState<>();
	ScreenState.State<AtlasScreen> PLACING_MARKER = new ScreenState.ToggleState<>(s -> s.addMarkerBookmark);
	ScreenState.State<AtlasScreen> DELETING_MARKER = new ScreenState.ToggleState<>(s -> s.deleteMarkerBookmark, s -> s.addChild(s.eraser), s -> s.removeChild(s.eraser));
	ScreenState.State<AtlasScreen> HIDING_MARKERS = new ScreenState.ToggleState<>(s -> s.markerVisibilityBookmark, s -> {
		s.markerVisibilityBookmark.setTitle(Component.translatable("gui.antique_atlas.showMarkers"));
		s.markerVisibilityBookmark.setIconTexture(ICON_SHOW_MARKERS);
	}, s -> {
		s.clearTargetBookmarks(s.playerBookmark);
		s.markerVisibilityBookmark.setTitle(Component.translatable("gui.antique_atlas.hideMarkers"));
		s.markerVisibilityBookmark.setIconTexture(ICON_HIDE_MARKERS);
	});

	int bookX();

	int bookY();

	int bookWidth();

	int bookHeight();

	int mapWidth();

	int mapHeight();

	double mapOffsetX();

	double mapOffsetY();

	int tilePixels();

	int tileChunks();

	int mapScale();

	Player player();

	WorldAtlasData worldAtlasData();

	double getPixelsPerBlock();

	double guiScale();

	ResourceKey<Level> dim();

	default int screenXToWorldX(double screenX) {
		return screenXToWorldX(screenX, bookX(), mapOffsetX(), mapWidth(), getPixelsPerBlock());
	}

	default int screenYToWorldZ(double screenY) {
		return screenYToWorldZ(screenY, bookY(), mapOffsetY(), mapHeight(), getPixelsPerBlock());
	}

	default double worldXToScreenX(double x) {
		return worldXToScreenX(x, bookX(), mapOffsetX(), mapWidth(), getPixelsPerBlock());
	}

	default double worldZToScreenY(double z) {
		return worldZToScreenY(z, bookY(), mapOffsetY(), mapHeight(), getPixelsPerBlock());
	}

	static int screenXToWorldX(double screenX, int bookX, double mapOffsetX, int mapWidth, double pixelsPerBlock) {
		double mapX = (int) Math.round(screenX - bookX - MAP_BORDER_WIDTH);
		return (int) Math.round((mapX - (mapWidth / 2f) - mapOffsetX) / pixelsPerBlock);
	}

	static int screenYToWorldZ(double screenY, int bookY, double mapOffsetY, int mapHeight, double pixelsPerBlock) {
		double mapY = (int) Math.round(screenY - bookY - MAP_BORDER_HEIGHT);
		return (int) Math.round((mapY - (mapHeight / 2f) - mapOffsetY) / pixelsPerBlock);
	}

	static double worldXToScreenX(double x, int bookX, double mapOffsetX, int mapWidth, double pixelsPerBlock) {
		double mapX = x * pixelsPerBlock + mapOffsetX + (mapWidth / 2f);
		return mapX + bookX + MAP_BORDER_WIDTH;
	}

	static double worldZToScreenY(double z, int bookY, double mapOffsetY, int mapHeight, double pixelsPerBlock) {
		double mapY = z * pixelsPerBlock + mapOffsetY + (mapHeight / 2f);
		return mapY + bookY + MAP_BORDER_HEIGHT;
	}

	default void renderMarker(PoseStack matrices, MultiBufferSource vertexConsumers, Landmark landmark, MarkerTexture texture, float z, int light, BiFunction<Double, Double, Float> alphaGetter, boolean pinned, boolean hovering, float markerScale) {
		BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		Integer color = landmark.get(LandmarkComponentTypes.COLOR);
		float[] accent = color == null ? null : ColorUtil.componentsFromRgb(color);
		float tint = hovering ? 0.8f : 1.0f;

		if (pos == null) {
			Set<ChunkPos> chunks = RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
			for (ChunkPos chunk : chunks) {
				double markerX = worldXToScreenX(chunk.getMinBlockX()) - bookX();
				double markerY = worldZToScreenY(chunk.getMinBlockZ()) - bookY();
				float effectiveScale = (float) (mapScale() / guiScale());
				matrices.pushPose();
				matrices.translate(markerX, markerY, 0.0);
				matrices.scale(effectiveScale, effectiveScale, 1.0F);
				int size = tilePixels() / tileChunks();
				int lineSize = tilePixels() / 16;
				if (size > 0) {
					float[] fillColor = accent == null ? ColorUtil.componentsFromRgb(0xFFFFFF) : new float[] { tint * accent[0], tint * accent[1], tint * accent[2] };
					float alpha = alphaGetter.apply(markerX, markerY);
					DrawUtil.fill(matrices, vertexConsumers, RenderType.textBackgroundSeeThrough(), z, light, 0, 0, size, size, 0.25F * alpha, fillColor);
					if (lineSize > 0) {
						if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) DrawUtil.fill(matrices, vertexConsumers, RenderType.textBackgroundSeeThrough(), z, light, 0, 0, lineSize, size, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) DrawUtil.fill(matrices, vertexConsumers, RenderType.textBackgroundSeeThrough(), z, light, 0, 0, size, lineSize, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) DrawUtil.fill(matrices, vertexConsumers, RenderType.textBackgroundSeeThrough(), z, light, size - lineSize, 0, size, size, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) DrawUtil.fill(matrices, vertexConsumers, RenderType.textBackgroundSeeThrough(), z, light, 0, size - lineSize, size, size, 0.5F * alpha, fillColor);
					}
				}
				matrices.popPose();
			}
			return;
		}

		double markerX = worldXToScreenX(pos.getX()) - bookX();
		double markerY = worldZToScreenY(pos.getZ()) - bookY();

		if (pinned) {
			markerX = Mth.clamp(markerX, MAP_BORDER_WIDTH, mapWidth() + MAP_BORDER_WIDTH);
			markerY = Mth.clamp(markerY, MAP_BORDER_HEIGHT, mapHeight() + MAP_BORDER_HEIGHT);
		}


		texture.draw(matrices, vertexConsumers, markerX, markerY, z, markerScale, tileChunks(), accent, tint, alphaGetter.apply(markerX, markerY), light);
	}

	default void renderPlayer(PoseStack matrices, MultiBufferSource vertexConsumers, float z, int light, PlayerSummary player, float iconScale, float alpha, boolean hovering, boolean self) {
		double dimX = player.pos().x();
		double dimZ = player.pos().z();

		boolean inDim = dim().equals(player.dimension());
		if (!inDim) {
			Map<ResourceKey<Level>, Integer> scales = AntiqueAtlas.CONFIG.dimensions.getScales(Minecraft.getInstance().getConnection());
			int newScale = scales.getOrDefault(dim(), 0);
			int oldScale = scales.getOrDefault(player.dimension(), 0);
			if (newScale * oldScale == 0) return; // no ratio!
			double mult = newScale / (double) oldScale;
			dimX = mult * dimX;
			dimZ = mult * dimZ;
		}

		double playerOffsetX = worldXToScreenX(dimX) - bookX();
		double playerOffsetY = worldZToScreenY(dimZ) - bookY();

		playerOffsetX = Mth.clamp(playerOffsetX, MAP_BORDER_WIDTH, mapWidth() + MAP_BORDER_WIDTH);
		playerOffsetY = Mth.clamp(playerOffsetY, MAP_BORDER_HEIGHT, mapHeight() + MAP_BORDER_HEIGHT);

		// Draw the icon:
		float tint = (player.online() ? 1 : 0.5f) * (hovering ? 0.9f : 1);
		float greenTint = self ? 1 : 0.7f;
		float redTint = inDim ? 1 : 0.7f;
		int argb = FastColor.ARGB32.color((int) (alpha * 255.0), (int) (tint * redTint * 255), (int) (tint * greenTint * 255), (int) (tint * 255));
		float playerRotation = ((float) Math.round(player.yaw() / 360f * PLAYER_ROTATION_STEPS) / PLAYER_ROTATION_STEPS) * 360f;

		DrawUtil.drawCenteredWithRotation(matrices, vertexConsumers, PLAYER, playerOffsetX, playerOffsetY, z, iconScale, PLAYER_ICON_WIDTH, PLAYER_ICON_HEIGHT, playerRotation, light, argb);
	}

	/** Expedition: the map is a coloured block map (see ColorMap); unexplored land shows the generator's biome sketch. */
	default void renderTiles(PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
		int chunkX0 = screenXToWorldX(bookX()) >> 4;
		int chunkZ0 = screenYToWorldZ(bookY()) >> 4;
		int chunkX1 = screenXToWorldX(bookX() + bookWidth()) >> 4;
		int chunkZ1 = screenYToWorldZ(bookY() + bookHeight()) >> 4;
		worldAtlasData().requestPreview(new Rect(chunkX0, chunkZ0, chunkX1, chunkZ1));
		double screenX0 = worldXToScreenX(0);
		double screenZ0 = worldZToScreenY(0);
		double pixelsPerBlock = (worldXToScreenX(4096) - screenX0) / 4096;
		worldAtlasData().colorMap().render(matrices, vertexConsumers, light, screenX0, screenZ0, pixelsPerBlock,
			bookX() + MAP_BORDER_WIDTH, bookY() + MAP_BORDER_HEIGHT, mapWidth(), mapHeight());
	}
}
