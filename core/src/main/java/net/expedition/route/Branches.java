package net.expedition.route;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * The story's middle: everyone plays one story around spawn (branch 0), one set of dungeons and one chapter, shared; a
 * dungeon played again is its NG+ (DungeonReset). Branches away from spawn were the old Sprout's: a world that has them
 * keeps their middles, no one goes there any more (Route.onLogin brings their players back).
 */
public final class Branches {
    private Branches() {
    }

    /** The middle of a branch: spawn for 0. */
    public static BlockPos middle(MinecraftServer server, int branch) {
        List<BlockPos> branches = RouteData.get(server).branches;
        return branch >= 1 && branch <= branches.size() ? branches.get(branch - 1) : server.overworld().getSharedSpawnPos();
    }
}
