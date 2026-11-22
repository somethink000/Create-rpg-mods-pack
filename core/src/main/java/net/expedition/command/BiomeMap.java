package net.expedition.command;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /expedition biomemap}: samples the level's biome source on a grid (no chunks generated) and writes
 * {@code biomemap.png} (labelled patches) and {@code biomemap.tsv} (share and patch sizes per biome).
 * With {@code surface}, each sample is taken at the generator's terrain height, else at a fixed y.
 */
final class BiomeMap {
    private BiomeMap() {
    }

    static String render(ServerLevel level, int centerX, int centerZ, int radius, int step, int y, boolean surface) throws Exception {
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        var source = generator.getBiomeSource();
        int size = radius * 2 / step;
        int[] ids = new int[size * size];
        int[] heights = new int[size * size];
        Map<String, Integer> index = new HashMap<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = centerX - radius + i * step;
                int z = centerZ - radius + j * step;
                int sy = surface ? generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1 : y;
                heights[j * size + i] = sy;
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sy), QuartPos.fromBlock(z), randomState.sampler());
                String name = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
                ids[j * size + i] = index.computeIfAbsent(name, n -> {
                    names.add(n);
                    return names.size() - 1;
                });
            }
        }
        // Connected patches (4-neighbour) per biome.
        int[] patch = new int[ids.length];
        java.util.Arrays.fill(patch, -1);
        java.util.List<int[]> patches = new java.util.ArrayList<>(); // {biome, area, sumX, sumY}
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < ids.length; start++) {
            if (patch[start] >= 0) {
                continue;
            }
            int id = patches.size();
            int[] p = {ids[start], 0, 0, 0};
            patches.add(p);
            patch[start] = id;
            queue.add(start);
            while (!queue.isEmpty()) {
                int c = queue.poll();
                int cx = c % size;
                int cy = c / size;
                p[1]++;
                p[2] += cx;
                p[3] += cy;
                int[][] next = {{cx + 1, cy}, {cx - 1, cy}, {cx, cy + 1}, {cx, cy - 1}};
                for (int[] n : next) {
                    if (n[0] >= 0 && n[1] >= 0 && n[0] < size && n[1] < size) {
                        int k = n[1] * size + n[0];
                        if (patch[k] < 0 && ids[k] == ids[start]) {
                            patch[k] = id;
                            queue.add(k);
                        }
                    }
                }
            }
        }
        int scale = Math.max(1, 1024 / size);
        BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int k = 0; k < ids.length; k++) {
            g.setColor(color(names.get(ids[k])));
            g.fillRect((k % size) * scale, (k / size) * scale, scale, scale);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int[] p : patches) {
            if (p[1] * scale * scale >= 900) {
                String label = names.get(p[0]).replaceFirst(".*:", "");
                int px = p[2] * scale / p[1];
                int py = p[3] * scale / p[1];
                g.setColor(Color.BLACK);
                g.drawString(label, px - label.length() * 3 + 1, py + 1);
                g.setColor(Color.WHITE);
                g.drawString(label, px - label.length() * 3, py);
            }
        }
        g.setColor(Color.RED);
        g.drawRect(size * scale / 2 - 2, size * scale / 2 - 2, 4, 4);
        g.dispose();
        Path dir = level.getServer().getServerDirectory();
        ImageIO.write(image, "png", dir.resolve("biomemap.png").toFile());
        String heightStats = "";
        if (surface) {
            // Relief: sea floor blue, lowland green, hills brown, mountains grey to white; 100-block contour lines dark.
            BufferedImage relief = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            int[] histogram = new int[8];
            for (int k = 0; k < heights.length; k++) {
                int h = heights[k];
                int rgb = h < level.getSeaLevel() ? 0x203070 + Math.max(0, h - level.getSeaLevel() + 40) * 2
                    : h < 90 ? 0x3C8C3C : h < 140 ? 0x7A8C3C : h < 200 ? 0x8C6A3C : h < 280 ? 0x8A8A8A : 0xF0F0F0;
                if (h % 100 < 3 && h > level.getSeaLevel()) {
                    rgb = 0x202020;
                }
                relief.setRGB(k % size, k / size, rgb);
                histogram[Math.max(0, Math.min(7, (h - level.getSeaLevel()) / 50 + 1))]++;
            }
            ImageIO.write(relief, "png", dir.resolve("heightmap.png").toFile());
            StringBuilder stats = new StringBuilder(", heights (sea, 0-50, 50-100, ... above sea):");
            for (int count : histogram) {
                stats.append(' ').append(100 * count / heights.length).append('%');
            }
            heightStats = stats.toString();
        }
        // Per biome: share of the map, patch count, mean patch area (blocks²).
        long[] area = new long[names.size()];
        int[] count = new int[names.size()];
        int small = 0;
        for (int[] p : patches) {
            area[p[0]] += p[1];
            count[p[0]]++;
            if ((long) p[1] * step * step < 128 * 128) {
                small += p[1];
            }
        }
        StringBuilder out = new StringBuilder("biome\tshare%\tpatches\tmean_patch_blocks2\n");
        for (int b = 0; b < names.size(); b++) {
            out.append(names.get(b)).append('\t').append(String.format("%.2f", 100.0 * area[b] / ids.length)).append('\t').append(count[b])
                .append('\t').append(area[b] * step * step / count[b]).append('\n');
        }
        Files.writeString(dir.resolve("biomemap.tsv"), out);
        long meanPatch = (long) ids.length * step * step / patches.size();
        return String.format("%d biomes, %d patches, mean patch %d blocks² (~%d blocks across), %.1f%% of the map in patches under 128x128",
            names.size(), patches.size(), meanPatch, (long) Math.sqrt(meanPatch), 100.0 * small / ids.length) + heightStats;
    }

    private static Color color(String name) {
        // Water reads as water: oceans deep blue (deeper darker), rivers and lakes light blue; the ice ridge white.
        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        if (path.contains("deep") && path.contains("ocean")) {
            return new Color(0x2A5FA8);
        }
        if (path.contains("ocean")) {
            return new Color(0x4F8FD8);
        }
        if (path.contains("river") || path.equals("lake") || path.contains("stream")) {
            return new Color(0x7FB8F0);
        }
        if (path.equals("ice_wall")) {
            return new Color(0xEAF4FF);
        }
        int h = name.hashCode() * 0x9E3779B1;
        return Color.getHSBColor((h >>> 8 & 0xFFFF) / 65536f, 0.45f + (h & 0x3F) / 256f, 0.55f + (h >>> 24 & 0x3F) / 200f);
    }
}
