package folk.sisby.surveyor.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Expedition: a sketch of the map beyond what was explored. The client asks for regions of 32x32 chunks it is looking
 * at; the server reads the biome every 4 blocks straight from the world generator's biome source (no chunk is
 * generated, microseconds a sample) and the sea floor's height every 2 chunks (a noise column each), and answers with a
 * palette, one byte per 4x4 blocks, the heights and the sea level. The atlas paints it like the explored map (water
 * where the ground lies under the sea, sand, stone and snow where they would be), a little faded.
 */
public final class BiomePreview {
	private BiomePreview() {
	}

	public static final int REGION_CHUNKS = 32;
	/** Biome samples per region side: one per 4 blocks. */
	public static final int QUARTS = REGION_CHUNKS * 4;
	/** Heights are sampled every this many chunks, region corners included: (32 / 2 + 1)^2 = 289 columns. */
	public static final int HEIGHT_STEP = 2;
	public static final int HEIGHT_GRID = REGION_CHUNKS / HEIGHT_STEP + 1;
	/** Requests one player may have in flight; more are dropped (the client asks again later). */
	private static final int MAX_PENDING = 48;
	private static final Map<UUID, AtomicInteger> PENDING = new ConcurrentHashMap<>();
	/**
	 * One low-priority thread of its own: the sketch must never take the shared worker pool (Util.backgroundExecutor),
	 * which loads and generates the chunks players stand in - on a small server it left them empty and froze the game.
	 */
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Surveyor-Preview");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	/**
	 * A region depends only on the world's seed: computed once for everyone, requests for one in progress share it. Kept
	 * while the server runs, at most CACHED regions (the oldest go first; ~17 KB each).
	 */
	private static final int CACHED = 2048;
	private static final Map<String, CompletableFuture<Region>> REGIONS = java.util.Collections.synchronizedMap(
		new java.util.LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Region>> eldest) {
				return size() > CACHED;
			}
		});

	/** Client side: where answered regions go (set by the atlas). */
	public static volatile Consumer<Region> CLIENT = region -> {
	};

	public record Request(int regionX, int regionZ) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("surveyor", "preview_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Request::regionX, ByteBufCodecs.VAR_INT, Request::regionZ, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * biomes: palette index per 4x4 blocks, row-major (z * 128 + x); heights: the sea floor's y (first air or water above
	 * the ground) on the grid, row-major (z * 17 + x), then the generator's sea level (seaLevel()).
	 */
	public record Region(ResourceKey<Level> dimension, int regionX, int regionZ, List<ResourceLocation> palette, byte[] biomes,
	                     List<Integer> heights) implements CustomPacketPayload {
		public int seaLevel() {
			return heights.size() > HEIGHT_GRID * HEIGHT_GRID ? heights.get(HEIGHT_GRID * HEIGHT_GRID) : 63;
		}

		public static final Type<Region> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("surveyor", "preview_region"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Region> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), Region::dimension,
			ByteBufCodecs.VAR_INT, Region::regionX,
			ByteBufCodecs.VAR_INT, Region::regionZ,
			ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(255)), Region::palette,
			ByteBufCodecs.BYTE_ARRAY, Region::biomes,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(HEIGHT_GRID * HEIGHT_GRID + 1)), Region::heights,
			Region::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register(PayloadRegistrar registrar) {
		registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				handle(player, request);
			}
		});
		registrar.playToClient(Region.TYPE, Region.CODEC, (region, context) -> context.enqueueWork(() -> CLIENT.accept(region)));
	}

	private static void handle(ServerPlayer player, Request request) {
		AtomicInteger pending = PENDING.computeIfAbsent(player.getUUID(), id -> new AtomicInteger());
		if (pending.incrementAndGet() > MAX_PENDING) {
			pending.decrementAndGet();
			return;
		}
		ServerLevel level = player.serverLevel();
		String key = level.dimension().location() + "/" + request.regionX() + "," + request.regionZ();
		CompletableFuture<Region> future;
		synchronized (REGIONS) {
			future = REGIONS.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> compute(level, request.regionX(), request.regionZ()), EXECUTOR));
		}
		future.whenComplete((region, error) -> level.getServer().execute(() -> {
				pending.decrementAndGet();
				if (error != null) {
					REGIONS.remove(key); // asked again later
				}
				if (region != null && !player.hasDisconnected()) {
					PacketDistributor.sendToPlayer(player, region);
				}
			}));
	}

	public static Region compute(ServerLevel level, int regionX, int regionZ) {
		var source = level.getChunkSource();
		var biomes = source.getGenerator().getBiomeSource();
		var sampler = source.randomState().sampler();
		// From the top of the world: above the terrain every column reads its surface biome, never a cave biome.
		int quartY = QuartPos.fromBlock(level.getMaxBuildHeight() - 1);
		Map<ResourceLocation, Integer> index = new HashMap<>();
		List<ResourceLocation> palette = new ArrayList<>();
		byte[] out = new byte[QUARTS * QUARTS];
		int quartX0 = regionX * QUARTS;
		int quartZ0 = regionZ * QUARTS;
		for (int z = 0; z < QUARTS; z++) {
			for (int x = 0; x < QUARTS; x++) {
				Holder<Biome> biome = biomes.getNoiseBiome(quartX0 + x, quartY, quartZ0 + z, sampler);
				ResourceLocation id = biome.unwrapKey().map(ResourceKey::location).orElse(null);
				if (id == null || palette.size() >= 255 && !index.containsKey(id)) {
					out[z * QUARTS + x] = (byte) 255; // unknown
					continue;
				}
				out[z * QUARTS + x] = (byte) (int) index.computeIfAbsent(id, key -> {
					palette.add(key);
					return palette.size() - 1;
				});
			}
		}
		List<Integer> heights = new ArrayList<>(HEIGHT_GRID * HEIGHT_GRID + 1);
		for (int z = 0; z < HEIGHT_GRID; z++) {
			for (int x = 0; x < HEIGHT_GRID; x++) {
				int blockX = (regionX * REGION_CHUNKS + x * HEIGHT_STEP) << 4;
				int blockZ = (regionZ * REGION_CHUNKS + z * HEIGHT_STEP) << 4;
				heights.add(source.getGenerator().getBaseHeight(blockX, blockZ, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
					level, source.randomState()));
			}
		}
		heights.add(source.getGenerator().getSeaLevel());
		return new Region(level.dimension(), regionX, regionZ, palette, out, heights);
	}
}
