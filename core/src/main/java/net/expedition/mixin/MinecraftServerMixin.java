package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.expedition.world.realm.RealmWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Overworld is created with the realm generator, the dimensions it absorbed are not created, and spawn is never in the sea. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @ModifyExpressionValue(method = "createLevels", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/Registry;get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", ordinal = 0))
    private Object expedition$realmOverworld(Object stem) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        return RealmWorld.wrapStem((LevelStem) stem, server.registryAccess(), server.getWorldData().worldGenOptions().seed());
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "setInitialSpawn", at = @At("TAIL"))
    private static void expedition$landSpawn(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.storage.ServerLevelData data,
                                             boolean bonusChest, boolean debug, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        RealmWorld.ensureLandSpawn(level);
        net.expedition.route.LandingCamp.build(level);
    }

    @ModifyExpressionValue(method = "createLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;entrySet()Ljava/util/Set;"))
    private Set<Map.Entry<?, ?>> expedition$skipMerged(Set<Map.Entry<?, ?>> stems) {
        return stems.stream().filter(entry -> !RealmWorld.isMerged((net.minecraft.resources.ResourceKey<?>) entry.getKey()))
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
