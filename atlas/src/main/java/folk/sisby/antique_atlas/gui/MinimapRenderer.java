package folk.sisby.antique_atlas.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.surveyor.client.SurveyorClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Expedition: the atlas as an always-on HUD minimap in the top-left corner (M opens the full atlas). */
public record MinimapRenderer(double mapOffsetX, double mapOffsetY, Player player, WorldAtlasData worldAtlasData, ResourceKey<Level> dim) implements AtlasRenderer {
	static final int SIZE = 96;
	static final int MARGIN = 4;
	/** 2 chunks per 16px tile: the 96px map covers ~192 blocks. */
	static final int TILE_CHUNKS = 2;

	private static long cachedTick = Long.MIN_VALUE;
	private static WorldAtlasData cachedData;
	private static java.util.Map<folk.sisby.surveyor.landmark.Landmark, folk.sisby.antique_atlas.MarkerTexture> cachedMarkers;
	private static java.util.Map<java.util.UUID, folk.sisby.surveyor.PlayerSummary> cachedFriends;

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen instanceof AtlasScreen) return;
		MinimapRenderer map = new MinimapRenderer(-player.getX() / TILE_CHUNKS, -player.getZ() / TILE_CHUNKS, player,
			WorldAtlasData.getOrCreate(player.level().dimension()), player.level().dimension());

		int x0 = MARGIN, y0 = MARGIN;
		g.fill(x0 - 1, y0 - 1, x0 + SIZE + 1, y0 + SIZE + 1, 0xFF000000);
		g.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xFF181818);
		g.flush();
		g.enableScissor(x0, y0, x0 + SIZE, y0 + SIZE);
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x0 - MAP_BORDER_WIDTH, y0 - MAP_BORDER_HEIGHT, 0);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		map.renderTiles(pose, null, MAX_LIGHT);
		// Every marker of the dimension and the friends' list change at most once a tick: gathered once a tick, not per frame.
		long tick = player.level().getGameTime();
		if (tick != cachedTick || cachedMarkers == null || cachedData != map.worldAtlasData()) {
			cachedMarkers = map.worldAtlasData().getAllMarkers(TILE_CHUNKS);
			cachedFriends = AntiqueAtlas.getOrderedFriends();
			cachedData = map.worldAtlasData();
			cachedTick = tick;
		}
		cachedMarkers.forEach((landmark, texture) ->
			map.renderMarker(pose, null, landmark, texture, 0, MAX_LIGHT, (x, y) -> 1.0F, false, false, 1.0F));
		cachedFriends.forEach((uuid, friend) ->
			map.renderPlayer(pose, null, 0, MAX_LIGHT, friend, 1.0F, 1.0F, false, uuid.equals(SurveyorClient.getClientUuid())));
		RenderSystem.disableBlend();
		pose.popPose();
		g.disableScissor();
	}

	@Override public int bookX() { return 0; }
	@Override public int bookY() { return 0; }
	@Override public int bookWidth() { return SIZE + 2 * MAP_BORDER_WIDTH; }
	@Override public int bookHeight() { return SIZE + 2 * MAP_BORDER_HEIGHT; }
	@Override public int mapWidth() { return SIZE; }
	@Override public int mapHeight() { return SIZE; }
	@Override public int tilePixels() { return 16; }
	@Override public int tileChunks() { return TILE_CHUNKS; }
	@Override public int mapScale() { return 1; }
	@Override public double guiScale() { return 1; }
	@Override public double getPixelsPerBlock() { return 16 / (16.0 * TILE_CHUNKS); }
}
