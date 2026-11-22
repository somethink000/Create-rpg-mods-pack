package net.expedition.route;

import java.util.List;
import net.expedition.Expedition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The world's spawn, built once when the world is created: the withered Tree, a leafless giant of unbreakable wood, over the graveyard of the
 * expedition that came to it long ago. One grave in front of the trunk is the player's: a root runs to it from the Tree,
 * and the player rises from it (Prologue ends there). Beside the graves the dead expedition's campfire still burns, for
 * the first fireseed. Nothing to loot. Marked on everyone's map.
 */
public final class LandingCamp {
    private LandingCamp() {
    }

    private static final int RADIUS = 25;
    /** The player stands on their grave this far south of the trunk. */
    private static final int GRAVE = 13;
    /** The trunk's height above the ground; the crown is its top. */
    static final int HEIGHT = 46;
    /** The trunk's radius at the foot (more at the buttresses) and at the top. */
    private static final double FOOT = 5.5;
    private static final double TOP = 1.5;
    private static final String[] STONES = {"gravestone", "cobblestone_gravestone", "mossy_cobblestone_gravestone", "deepslate_gravestone",
        "cracked_blackstone_gravestone"};

    public static void build(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunkAt(spawn); // an unloaded chunk's heightmap reads as the world's bottom
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        int groundY = top.getY() - 1;
        BlockState surface = level.getBlockState(top.below());
        if (!surface.isSolid() || surface.is(Blocks.SNOW_BLOCK)) {
            surface = Blocks.GRASS_BLOCK.defaultBlockState();
        }
        RandomSource random = RandomSource.create(level.getSeed());
        BlockPos tree = new BlockPos(spawn.getX(), groundY + 1, spawn.getZ());
        // A level clearing: dead earth under the Tree, the surface further out, air above, earth under it down to the ground.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int square = dx * dx + dz * dz;
                if (square > RADIUS * RADIUS + RADIUS) {
                    continue;
                }
                BlockPos at = new BlockPos(tree.getX() + dx, groundY, tree.getZ() + dz);
                BlockState ground = square <= 120 || random.nextInt(square) < 120 ? (random.nextInt(3) == 0 ? Blocks.ROOTED_DIRT : random.nextBoolean()
                    ? Blocks.COARSE_DIRT : Blocks.PODZOL).defaultBlockState() : surface;
                level.setBlock(at, ground, Block.UPDATE_CLIENTS);
                for (int y = 1; y <= HEIGHT + 14; y++) {
                    level.setBlock(at.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int y = 1; y <= 12 && level.getBlockState(at.below(y)).canBeReplaced(); y++) {
                    level.setBlock(at.below(y), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        trunk(level, tree, random);
        graves(level, tree, random);
        // The player's grave: a stone at the head (towards the Tree), a fresh mound; a root of the Tree runs down to it.
        BlockPos head = tree.south(GRAVE - 2);
        clear(level, head, 3);
        level.setBlock(head, stone(STONES[0]), Block.UPDATE_ALL);
        engrave(level, head, "you.1", "you.2", "you.3", "you.4");
        level.setBlock(tree.south(GRAVE - 1).below(), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(tree.south(GRAVE).below(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
        // The dead expedition's camp past the graves: its fire still burns, benches by it, soul lanterns on posts.
        BlockPos fire = tree.south(GRAVE + 6);
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        for (Direction side : List.of(Direction.EAST, Direction.WEST)) {
            level.setBlock(fire.relative(side, 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z), Block.UPDATE_ALL);
        }
        for (int[] d : List.of(new int[] {6, 19}, new int[] {-6, 19}, new int[] {19, 9}, new int[] {-19, 9})) {
            BlockPos post = tree.offset(d[0], 0, d[1]);
            clear(level, post, 3);
            level.setBlock(post, Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(post.above(), Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(post.above(2), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos standing = tree.south(GRAVE);
        clear(level, standing, 2);
        level.setDefaultSpawnPos(standing, 180); // facing the Tree
        mark(level, tree);
        Expedition.LOG.info("Withered Tree built at {}, the grave at {}", tree.toShortString(), standing.toShortString());
    }

    /** Air at a spot and above it (a root or branch of the Tree in the way of a grave or a post). */
    private static void clear(ServerLevel level, BlockPos pos, int height) {
        for (int y = 0; y < height; y++) {
            level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The giant: a fluted trunk of withered wood, buttressed at the foot, leaning a little and thinning to the top, where it
     * forks; roots spread over the ground (one to the player's grave, the rest away from the graves); great bare branches
     * climb out of its upper half and split.
     */
    private static void trunk(ServerLevel level, BlockPos tree, RandomSource random) {
        BlockState wood = net.expedition.content.StructureFacades.WITHERED_WOOD.get().defaultBlockState();
        double leanAngle = random.nextDouble() * Math.PI * 2;
        Vec3 axis = Vec3.ZERO;
        for (int y = -2; y <= HEIGHT; y++) {
            double share = Math.max(0, y) / (double) HEIGHT;
            double radius = FOOT - (FOOT - TOP) * Math.pow(share, 0.8) + (y < 6 ? (6 - y) * 0.45 : 0);
            axis = new Vec3(Math.cos(leanAngle) * 2.5 * Math.sin(share * 1.8), y, Math.sin(leanAngle) * 2.5 * Math.sin(share * 1.8));
            int reach = (int) Math.ceil(radius * 1.25) + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double x = dx - axis.x;
                    double z = dz - axis.z;
                    // Fluted: ridges and furrows around the trunk, deeper at the buttressed foot.
                    double flute = 1 + (y < 8 ? 0.22 : 0.1) * Math.cos(6 * Math.atan2(z, x) + share * 3);
                    if (x * x + z * z <= radius * radius * flute * flute) {
                        level.setBlock(tree.offset(dx, y, dz), wood, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        Vec3 crown = new Vec3(axis.x, HEIGHT, axis.z);
        // The top forks into a few thick limbs going up and out.
        for (int i = 0; i < 3; i++) {
            double angle = leanAngle + i * 2.1;
            limb(level, wood, tree, crown, crown.add(Math.cos(angle) * 6, 9 + random.nextInt(4), Math.sin(angle) * 6), 1.3, 0.5);
        }
        // Roots: one south to the grave, the others round the north half, east and west, over and into the ground.
        limb(level, wood, tree, new Vec3(0, 1, FOOT - 1), new Vec3(0, -0.4, GRAVE - 3), 1.6, 0.6);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * (0.95 + 1.1 * i / 7.0) + (random.nextDouble() - 0.5) * 0.3; // west through north to east
            double length = 11 + random.nextInt(6);
            Vec3 from = new Vec3(Math.cos(angle) * (FOOT - 1), 2, Math.sin(angle) * (FOOT - 1));
            Vec3 bend = new Vec3(Math.cos(angle + 0.25) * length * 0.55, 0.3, Math.sin(angle + 0.25) * length * 0.55);
            Vec3 to = new Vec3(Math.cos(angle - 0.1) * length, -1.2, Math.sin(angle - 0.1) * length);
            limb(level, wood, tree, from, bend, 2.0, 1.2);
            limb(level, wood, tree, bend, to, 1.2, 0.5);
        }
        // Branches: great limbs out of the upper half, rising, each splitting in two.
        for (int i = 0; i < 9; i++) {
            double angle = leanAngle + i * 2.4 + random.nextDouble() * 0.4;
            double height = 20 + i * 2.4 + random.nextInt(3);
            double share = height / HEIGHT;
            Vec3 from = new Vec3(Math.cos(leanAngle) * 2.5 * Math.sin(share * 1.8), height, Math.sin(leanAngle) * 2.5 * Math.sin(share * 1.8));
            double length = 15 - i * 0.6 + random.nextInt(3);
            Vec3 mid = from.add(Math.cos(angle) * length * 0.6, length * 0.3, Math.sin(angle) * length * 0.6);
            Vec3 end = from.add(Math.cos(angle) * length, length * 0.45, Math.sin(angle) * length);
            limb(level, wood, tree, from, mid, 1.9 - i * 0.08, 1.1);
            limb(level, wood, tree, mid, end, 1.1, 0.5);
            double fork = angle + (random.nextBoolean() ? 0.8 : -0.8);
            limb(level, wood, tree, mid, mid.add(Math.cos(fork) * 6, 4 + random.nextInt(3), Math.sin(fork) * 6), 0.9, 0.5);
        }
    }

    /** A limb from one point to another (relative to the tree's foot), its thickness tapering from one radius to the other. */
    private static void limb(ServerLevel level, BlockState wood, BlockPos tree, Vec3 from, Vec3 to, double fromRadius, double toRadius) {
        int steps = (int) Math.ceil(from.distanceTo(to) * 2) + 1;
        for (int s = 0; s <= steps; s++) {
            double k = s / (double) steps;
            Vec3 at = from.lerp(to, k);
            double radius = fromRadius + (toRadius - fromRadius) * k;
            int reach = (int) Math.ceil(radius);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        BlockPos pos = tree.offset((int) Math.floor(at.x + 0.5) + dx, (int) Math.floor(at.y + 0.5) + dy, (int) Math.floor(at.z + 0.5) + dz);
                        if (pos.distToCenterSqr(tree.getX() + at.x, tree.getY() + at.y, tree.getZ() + at.z) <= radius * radius + 0.3) {
                            level.setBlock(pos, wood, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    /** The expedition's graves in rows either side of the player's: a stone, a mound in front of it, some bones and candles. */
    private static void graves(ServerLevel level, BlockPos tree, RandomSource random) {
        for (int dx : new int[] {-16, -12, -8, 8, 12, 16}) {
            for (int dz : new int[] {6, 10, 14}) {
                if (random.nextInt(5) == 0) {
                    continue;
                }
                BlockPos stone = tree.offset(dx, 0, dz);
                clear(level, stone, 3);
                clear(level, stone.south(), 3);
                clear(level, stone.south(2), 3);
                level.setBlock(stone, stone(STONES[random.nextInt(STONES.length)]), Block.UPDATE_ALL);
                engrave(level, stone, null, "other", "other.2", null);
                level.setBlock(stone.south().below(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(stone.south(2).below(), Blocks.PODZOL.defaultBlockState(), Block.UPDATE_CLIENTS);
                int extra = random.nextInt(6);
                if (extra == 0) {
                    level.setBlock(stone.south(), block("bone_remains", Blocks.AIR), Block.UPDATE_ALL);
                } else if (extra == 1) {
                    level.setBlock(stone.south(), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
                } else if (extra == 2) {
                    level.setBlock(stone.south(), Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Words on a gravestone's face (it keeps sign text): lang keys expedition.story.grave.<key>, null for an empty line. */
    private static void engrave(ServerLevel level, BlockPos pos, String... keys) {
        var entity = level.getBlockEntity(pos);
        if (entity == null) {
            return;
        }
        var registries = level.registryAccess();
        net.minecraft.nbt.CompoundTag tag = entity.saveWithoutMetadata(registries);
        net.minecraft.nbt.ListTag messages = new net.minecraft.nbt.ListTag();
        for (String key : keys) {
            messages.add(net.minecraft.nbt.StringTag.valueOf(key == null ? "\"\""
                : "{\"translate\":\"expedition.story.grave." + key + "\"}"));
        }
        net.minecraft.nbt.CompoundTag front = new net.minecraft.nbt.CompoundTag();
        front.put("messages", messages);
        front.putString("color", "black");
        front.putBoolean("has_glowing_text", false);
        tag.put("front_text", front);
        entity.loadWithComponents(tag, registries);
        entity.setChanged();
    }

    /** A gravestone of the Graveyard mod facing the mound before it (south), or mossy cobblestone wall without the mod. */
    private static BlockState stone(String name) {
        return block(name, Blocks.MOSSY_COBBLESTONE_WALL);
    }

    private static BlockState block(String name, Block fallback) {
        BlockState state = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("graveyard", name))
            .orElse(fallback).defaultBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH) : state;
    }

    /** The player's grave, where they rise (the world's spawn). */
    public static BlockPos grave(ServerLevel level) {
        return level.getSharedSpawnPos();
    }

    /** The foot of the Tree's trunk. */
    public static BlockPos tree(ServerLevel level) {
        return level.getSharedSpawnPos().relative(Direction.NORTH, GRAVE);
    }

    /** The Tree on everyone's map. */
    private static void mark(ServerLevel level, BlockPos pos) {
        var landmarks = folk.sisby.surveyor.landmark.WorldLandmarks.of(level);
        if (landmarks == null) {
            return;
        }
        var id = ResourceLocation.fromNamespaceAndPath("antique_atlas", "custom/bed/expedition/landing_camp");
        landmarks.put(folk.sisby.surveyor.landmark.Landmark.create(folk.sisby.surveyor.landmark.WorldLandmarks.GLOBAL, id,
            builder -> builder.add(folk.sisby.surveyor.landmark.component.LandmarkComponentTypes.POS, pos)
                .add(folk.sisby.surveyor.landmark.component.LandmarkComponentTypes.NAME, Component.translatable("expedition.story.camp"))
                .add(folk.sisby.surveyor.landmark.component.LandmarkComponentTypes.COLOR, 0x3C7AB0)));
    }
}
