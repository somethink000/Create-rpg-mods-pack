package net.expedition.command;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * {@code /expedition topview}: a map of generated chunks (like a filled map, shaded by height) to {@code topview.png}.
 * With {@code y}: the first solid block at or below y (a cave layer). Pregenerate the area with Chunky first.
 * Runs on the server thread.
 */
final class TopView {
    private TopView() {
    }

    static String render(ServerLevel level, int centerX, int centerZ, int radius, Integer fromY) throws Exception {
        int size = radius * 2;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int missing = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = (centerX - radius) >> 4; cx <= (centerX + radius - 1) >> 4; cx++) {
            for (int cz = (centerZ - radius) >> 4; cz <= (centerZ + radius - 1) >> 4; cz++) {
                // Loads from disk (generates if missing: pregenerate with Chunky to keep this quick).
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, true);
                if (chunk == null) {
                    missing++;
                    continue;
                }
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        int x = (cx << 4) + dx;
                        int z = (cz << 4) + dz;
                        int ix = x - centerX + radius;
                        int iz = z - centerZ + radius;
                        if (ix < 0 || iz < 0 || ix >= size || iz >= size) {
                            continue;
                        }
                        int y = fromY != null ? fromY : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                        BlockState state = null;
                        for (; y > level.getMinBuildHeight(); y--) {
                            state = chunk.getBlockState(pos.set(x, y, z));
                            if (!state.isAir()) {
                                break;
                            }
                        }
                        if (state == null || state.isAir()) {
                            image.setRGB(ix, iz, 0x101018);
                            continue;
                        }
                        MapColor color = state.getMapColor(level, pos);
                        int rgb = color == MapColor.NONE ? 0x808080 : color.col;
                        double shade = 0.55 + 0.45 * Math.min(1, Math.max(0, (y + 64) / 320.0));
                        int r = (int) ((rgb >> 16 & 255) * shade);
                        int g = (int) ((rgb >> 8 & 255) * shade);
                        int b = (int) ((rgb & 255) * shade);
                        image.setRGB(ix, iz, r << 16 | g << 8 | b);
                    }
                }
            }
        }
        ImageIO.write(image, "png", level.getServer().getServerDirectory().resolve("topview.png").toFile());
        return "topview " + size + "x" + size + " at " + centerX + "," + centerZ + (fromY != null ? " from y=" + fromY : "") + ", chunks not generated: " + missing;
    }
}
