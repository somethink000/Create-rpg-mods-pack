package net.expedition.mixin;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A possessed Steam 'n' Rails conductor (a drone pilot) pulls levers and presses buttons on a ship too. Sable keeps a
 * ship's blocks in a far-off plot, so the conductor's own reach and line-of-sight checks (plain world distance) refused
 * every block aboard: here reach is measured through Sable, and the block is used directly.
 */
@Mixin(targets = "com.railwayteam.railways.content.conductor.ConductorEntity", remap = false)
abstract class ConductorShipLeverMixin {
    @Shadow
    private ServerPlayer fakePlayer;

    @Shadow
    public static boolean canSpyInteract(BlockState state) {
        throw new AssertionError();
    }

    /** The conductor's reach (its REACH, 3x2x3), squared. */
    private static final double EXPEDITION$REACH_SQR = 3 * 3 + 2 * 2 + 3 * 3;

    private static Object expedition$sable;
    private static Method expedition$getContaining;
    private static Method expedition$distanceSqr;

    @Inject(method = "onSpyInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void expedition$onShip(BlockPos pos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (fakePlayer == null || !expedition$onSubLevel(level, pos)) {
            return; // in the world: the conductor's own handling
        }
        ci.cancel();
        BlockState state = level.getBlockState(pos);
        if (!canSpyInteract(state) || expedition$distanceSqr(level, self.position(), Vec3.atCenterOf(pos)) > EXPEDITION$REACH_SQR) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        if (!CommonHooks.onRightClickBlock(fakePlayer, InteractionHand.MAIN_HAND, pos, hit).isCanceled()) {
            state.useWithoutItem(level, fakePlayer, hit);
        }
    }

    private static boolean expedition$onSubLevel(Level level, Vec3i pos) {
        try {
            if (expedition$getContaining == null) {
                Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
                expedition$sable = sable.getField("HELPER").get(null);
                expedition$getContaining = expedition$sable.getClass().getMethod("getContaining", Level.class, Vec3i.class);
                expedition$distanceSqr = expedition$sable.getClass().getMethod("distanceSquaredWithSubLevels", Level.class, Position.class, Position.class);
            }
            return expedition$getContaining.invoke(expedition$sable, level, pos) != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false; // no Sable: no ships, nothing to fix
        }
    }

    private static double expedition$distanceSqr(Level level, Position a, Position b) {
        try {
            return (double) expedition$distanceSqr.invoke(expedition$sable, level, a, b);
        } catch (ReflectiveOperationException e) {
            return Double.MAX_VALUE;
        }
    }
}
