package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.expedition.world.realm.Realm;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Piglins and hoglins are at home in the Nether realm: they don't turn into zombies there. */
@Mixin({AbstractPiglin.class, Hoglin.class})
abstract class PiglinMixin {
    @ModifyExpressionValue(method = "isConverting", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;piglinSafe()Z"))
    private boolean expedition$netherRealm(boolean safe) {
        Entity self = (Entity) (Object) this;
        return safe || RealmWorld.realmAt(self.level(), self.blockPosition()) == Realm.NETHER;
    }
}
