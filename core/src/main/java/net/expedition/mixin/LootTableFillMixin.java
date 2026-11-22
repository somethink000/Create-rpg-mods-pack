package net.expedition.mixin;

import net.expedition.content.ChestLoot;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After every loot modifier (any mod, any order) filled a container: the final clean-up of what may lie in it. */
@Mixin(LootTable.class)
abstract class LootTableFillMixin {
    @Inject(method = "fill", at = @At("RETURN"))
    private void expedition$sanitize(Container container, LootParams params, long seed, CallbackInfo ci) {
        ChestLoot.sanitize(container, params);
    }
}
