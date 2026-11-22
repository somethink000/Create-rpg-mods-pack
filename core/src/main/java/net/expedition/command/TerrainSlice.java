package net.expedition.command;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /expedition slice}: a vertical cut of the generator's raw terrain (noise and fluids, before surface rules and
 * features) along +X from the given point, written to {@code slice.png}: air white, water blue, lava orange, ground grey.
 */
final class TerrainSlice {
    private TerrainSlice() {
    }

    static String render(ServerLevel level, int startX, int z, int length, int step) throws Exception {
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        int width = length / step;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < width; i++) {
            var column = generator.getBaseColumn(startX + i * step, z, level, randomState);
            for (int y = 0; y < height; y++) {
                BlockState state = column.getBlock(minY + y);
                int rgb = state.isAir() ? 0xF4F4F4 : state.is(Blocks.WATER) ? 0x3A6FD8 : state.is(Blocks.LAVA) ? 0xF07A1A
                    : !state.getFluidState().isEmpty() ? 0x2BB3A0 : (minY + y) % 64 == 0 ? 0x505050 : 0x7A7A7A;
                image.setRGB(i, height - 1 - y, rgb);
            }
        }
        // The router's depth at y 64 is Tectonic's surface offset (+0.5): above 0 the ground stands over sea level.
        StringBuilder depth = new StringBuilder();
        var router = randomState.router();
        for (int x = startX; x < startX + length; x += Math.max(8, length / 64)) {
            depth.append(String.format(" %.3f", router.depth().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 64, z))));
        }
        net.expedition.Expedition.LOG.info("slice depth at y 64:{}", depth);
        StringBuilder continents = new StringBuilder();
        for (int x = startX; x < startX + length; x += Math.max(8, length / 64)) {
            continents.append(String.format(" %.2f/%.2f", router.ridges().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 64, z)), router.continents().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 64, z))));
        }
        net.expedition.Expedition.LOG.info("slice ridges/continents:{}", continents);
        ImageIO.write(image, "png", level.getServer().getServerDirectory().resolve("slice.png").toFile());
        return "slice " + length + " blocks from x=" + startX + " z=" + z + " (rows of y%64==0 darker)";
    }
}
