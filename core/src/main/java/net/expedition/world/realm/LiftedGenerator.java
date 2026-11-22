package net.expedition.world.realm;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The realm world's generator as a lifted realm's own structures see it while they lay themselves out: heights in the
 * realm's native frame (the real ones minus its shift). The laid-out structure is then lifted by the shift as a whole,
 * so absolute-height and terrain-following structures alike land where their dimension would have put them.
 */
final class LiftedGenerator extends NoiseBasedChunkGenerator {
    private final NoiseBasedChunkGenerator real;
    private final int shift;
    /** The realm dimension's floor: an empty (void) column reads as this, as it did there. */
    private final int nativeMinY;

    LiftedGenerator(NoiseBasedChunkGenerator real, int shift, int nativeMinY) {
        super(real.getBiomeSource(), real.generatorSettings());
        this.real = real;
        this.shift = shift;
        this.nativeMinY = nativeMinY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.max(nativeMinY, real.getBaseHeight(x, z, type, level, random) - shift);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = real.getBaseColumn(x, z, height, random);
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = column.getBlock(height.getMinBuildHeight() + i);
        }
        return new NoiseColumn(height.getMinBuildHeight() - shift, states);
    }

    @Override
    public int getSeaLevel() {
        return real.getSeaLevel() - shift;
    }
}
