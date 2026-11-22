package net.expedition.world.realm;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * A kind of land of the one world that carries a former dimension: its biomes, terrain, blocks and fluids come from that
 * dimension's own generator. Each realm cell of the world holds one biome of its realm (see RealmLayout), as big as an
 * Overworld biome. The realm's terrain is lifted by {@link #shift} so that its "sea level" meets the Overworld's (63):
 * no walls and pits at the borders; its own structures are generated in its native heights and lifted with it.
 * {@code weight}: share of the world's cells.
 */
public enum Realm {
    OVERWORLD("minecraft:overworld", 45, false, 0, 0, 1),
    /** Twilight's water level 0 -> 63. */
    TWILIGHT("twilightforest:twilight_forest", 15, true, 63, 0, 4),
    /** Lava sea 31 -> 63; the roof is cut away above native y 48: open scorched land over lava lakes. */
    NETHER("minecraft:the_nether", 12, true, 32, 48, 1),
    /** Sculk grime sea 17 -> 63; opened like the Nether. */
    OTHERSIDE("deeperdarker:otherside", 8, true, 46, 30, 1),
    /** Islands over the void, as the End always was. */
    END("minecraft:the_end", 10, false, 0, 0, 1),
    /** Islands lifted into the sky above the Overworld's land, over the void. */
    AETHER("aether:the_aether", 10, false, 96, 0, 1);

    public final ResourceKey<LevelStem> stem;
    final int weight;
    /** Below its generator's range the ground continues solid (else: void). */
    final boolean solidFloor;
    /** Blocks the realm's terrain is lifted by. */
    public final int shift;
    /** If not 0: the realm's roof fades away above this native y (an open realm instead of a cave world). */
    final int openFrom;
    /**
     * Twilight's terrain follows its own biome grid, which is small: the grid and the terrain are both stretched this
     * many times horizontally, so the terrain's hills are as wide as the world's.
     */
    final int stretch;

    /** Biomes of the realm that are not lands of their own (streams, lakes, underground layers, cloud layers, the main End island). */
    static final Set<String> NOT_A_LAND = Set.of("twilightforest:stream", "twilightforest:lake", "twilightforest:underground",
        "twilightforest:highlands_underground", "deep_aether:cloud", "deep_aether:overgrown_cloud", "minecraft:the_end");

    Realm(String stem, int weight, boolean solidFloor, int shift, int openFrom, int stretch) {
        this.stem = ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.parse(stem));
        this.weight = weight;
        this.solidFloor = solidFloor;
        this.shift = shift;
        this.openFrom = openFrom;
        this.stretch = stretch;
    }

    /** Open realms sit at the Overworld's sea level: a low rim along their border keeps the neighbour's sea out. */
    int rimTop() {
        return openFrom != 0 ? 70 : 0;
    }

    /** For island realms (over the void): is there land at this column? Always true elsewhere. */
    public boolean hasGround(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (solidFloor || this == OVERWORLD) {
            return true;
        }
        var source = level.getChunkSource();
        int y = source.getGenerator().getBaseHeight(pos.getX(), pos.getZ(), net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
            level, source.randomState());
        return y > level.getMinBuildHeight() + 8;
    }
}
