package net.expedition.route;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Camera shots of story scenes, worked out on the server where the whole world is known: the camera stands at a point
 * that is free of blocks and sees what it shows, and moves straight towards it, a few blocks, over the scene. Every
 * point of that way is checked, so the camera never passes through a block. Null when no such point exists (the scene
 * is then only heard).
 */
public final class Shots {
    private Shots() {
    }

    /** The camera's way (from, to) and the point it looks at. */
    public record Path(Vec3 from, Vec3 to, Vec3 focus) {
    }

    private static final int DIRECTIONS = 16;

    /** Close on a creature (a boss, the player): a few of its widths away, near its eye height. */
    public static Path ofEntity(ServerLevel level, Entity entity) {
        Vec3 focus = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
        double near = 3.5 + entity.getBbWidth() * 1.5;
        return search(level, focus, near + 7, near, new double[] {1, 2.5, 0, 4, 6}, Math.min(4, near * 0.4), false, 0);
    }

    /** Interior shot: points tried in the dungeon's pieces, rays cast from each, how far a ray looks. */
    private static final int INSIDE_SAMPLES = 400;
    private static final int RAYS = 24;
    private static final double RAY_LENGTH = 24;

    /**
     * A dungeon from inside, the camera standing still: at the most open point within its pieces (free of blocks, a
     * man's height or two over a floor, under a roof if any point is) looking down its longest view. Null if no point
     * of its pieces is open.
     */
    public static Path inside(ServerLevel level, net.minecraft.world.level.levelgen.structure.StructureStart start) {
        var pieces = start.getPieces();
        if (pieces.isEmpty()) {
            return null;
        }
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(start.getChunkPos().toLong());
        Vec3 best = null;
        Vec3 bestLook = null;
        double bestScore = 0;
        boolean bestCovered = false;
        for (int i = 0; i < INSIDE_SAMPLES; i++) {
            BoundingBox box = pieces.get(random.nextInt(pieces.size())).getBoundingBox();
            // A column of the piece, top down: its floors (air over something solid), one of them at random; the eye a
            // man's height or so over it (big halls have their floor far below most points of their box).
            int x = box.minX() + random.nextInt(box.getXSpan());
            int z = box.minZ() + random.nextInt(box.getZSpan());
            java.util.List<Integer> floors = new java.util.ArrayList<>();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, box.maxY(), z);
            boolean airAbove = false;
            for (int y = box.maxY(); y > box.minY(); y--) {
                at.setY(y);
                boolean air = level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getFluidState(at).isEmpty();
                at.setY(y - 1);
                if (air && airAbove && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    floors.add(y);
                }
                airAbove = air;
            }
            if (floors.isEmpty()) {
                continue;
            }
            Vec3 eye = new Vec3(x + 0.5, floors.get(random.nextInt(floors.size())) + 1.6 + random.nextDouble() * 1.2, z + 0.5);
            if (!free(level, eye, eye, false)) {
                continue;
            }
            boolean covered = eye.y < height(level, Heightmap.Types.MOTION_BLOCKING, Mth.floor(eye.x), Mth.floor(eye.z));
            if (bestCovered && !covered) {
                continue;
            }
            double score = 0;
            double longest = 0;
            Vec3 look = null;
            for (int r = 0; r < RAYS; r++) {
                double yaw = Math.PI * 2 * r / 16;
                double pitch = r < 16 ? -0.15 : -0.5;
                Vec3 direction = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                Vec3 end = eye.add(direction.scale(RAY_LENGTH));
                HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
                double reach = hit.getType() == HitResult.Type.MISS ? RAY_LENGTH : hit.getLocation().distanceTo(eye);
                score += reach;
                if (r < 16 && reach > longest) {
                    longest = reach;
                    look = direction;
                }
            }
            if ((covered && !bestCovered) || score > bestScore) {
                best = eye;
                bestLook = look;
                bestScore = score;
                bestCovered = covered;
            }
        }
        if (best == null || bestLook == null) {
            return null;
        }
        return new Path(best, best, best.add(bestLook.scale(10)));
    }

    /** A place in the open (the camp's fire): from above the ground, 8 to 16 blocks away. */
    public static Path ofPlace(ServerLevel level, Vec3 focus) {
        return search(level, focus, 16, 8, new double[] {5, 3, 8, 11}, 4, true, 0);
    }

    /**
     * A lair from outside: the middle of what stands above the land around it (its frame from that land to its roof),
     * from a little more than its size away, above the ground. Null for a lair with nothing above the ground.
     */
    public static Path ofLair(ServerLevel level, BoundingBox box) {
        int floor = level.getMinBuildHeight() + 1;
        int around = Integer.MAX_VALUE;
        for (int[] corner : new int[][] {{box.minX() - 4, box.minZ() - 4}, {box.maxX() + 4, box.minZ() - 4}, {box.minX() - 4, box.maxZ() + 4},
            {box.maxX() + 4, box.maxZ() + 4}}) {
            int h = height(level, Heightmap.Types.OCEAN_FLOOR, corner[0], corner[1]);
            around = Math.min(around, h <= floor ? box.minY() : h);
        }
        int roof = floor;
        int step = Math.max(4, Math.max(box.getXSpan(), box.getZSpan()) / 12);
        for (int x = box.minX(); x <= box.maxX(); x += step) {
            for (int z = box.minZ(); z <= box.maxZ(); z += step) {
                roof = Math.max(roof, Math.min(box.maxY(), height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1));
            }
        }
        int bottom = Math.max(around, box.minY());
        if (roof < bottom + 3) {
            return null;
        }
        Vec3 focus = new Vec3(box.getCenter().getX() + 0.5, bottom + (roof - bottom) * 0.6, box.getCenter().getZ() + 0.5);
        double size = Math.max(Math.max(box.getXSpan(), box.getZSpan()), roof - bottom);
        double far = Mth.clamp(size * 0.8 + 10, 20, 64);
        return search(level, focus, far, far * 0.6, new double[] {far * 0.4, far * 0.3, far * 0.55, far * 0.75}, far * 0.25, true, 0.4);
    }

    /**
     * The first camera way found, the farthest distances first: 16 directions, the given heights above the focus. The
     * way runs {@code push} blocks towards the focus; every half block of it is free, and its ends see the focus (a ray
     * may stop on the shown thing itself within {@code nearShare} of the distance).
     */
    private static Path search(ServerLevel level, Vec3 focus, double far, double near, double[] heights, double push, boolean aboveGround,
        double nearShare) {
        for (double distance = far; distance >= near - 0.01; distance -= Math.max(1, (far - near) / 4)) {
            for (double height : heights) {
                for (int i = 0; i < DIRECTIONS; i++) {
                    double angle = Math.PI * 2 * i / DIRECTIONS + Math.PI / 5;
                    Vec3 from = focus.add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
                    Vec3 to = from.add(focus.subtract(from).normalize().scale(push));
                    if (free(level, from, to, aboveGround) && sees(level, from, focus, nearShare) && sees(level, to, focus, nearShare)) {
                        return new Path(from, to, focus);
                    }
                }
            }
        }
        return null;
    }

    /** Every half block of the way has room around the camera (no block within 0.4), above the ground if asked. */
    private static boolean free(ServerLevel level, Vec3 from, Vec3 to, boolean aboveGround) {
        int steps = (int) Math.ceil(from.distanceTo(to) * 2);
        for (int s = 0; s <= steps; s++) {
            Vec3 at = from.lerp(to, s / (double) Math.max(1, steps));
            for (int corner = 0; corner < 8; corner++) {
                BlockPos pos = BlockPos.containing(at.x + ((corner & 1) == 0 ? -0.4 : 0.4), at.y + ((corner & 2) == 0 ? -0.4 : 0.4),
                    at.z + ((corner & 4) == 0 ? -0.4 : 0.4));
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
                    return false;
                }
            }
            if (aboveGround && at.y < height(level, Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sees(ServerLevel level, Vec3 eye, Vec3 focus, double nearShare) {
        HitResult hit = level.clip(new ClipContext(eye, focus, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(focus) <= eye.distanceTo(focus) * nearShare;
    }

    /** A heightmap read that loads the chunk (Level.getHeight answers the world's floor for a chunk not loaded). */
    static int height(ServerLevel level, Heightmap.Types type, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1;
    }
}
