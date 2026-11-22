package net.expedition.mixin;

import net.expedition.content.StructureFacades;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure templates from mod/vanilla data load with wool, bookshelves and metal blocks as facades and without hay (see
 * StructureFacades). Only data templates: a player's own saved structures and Create schematics stay as built.
 */
@Mixin(StructureTemplateManager.class)
abstract class StructureTemplateMixin {
    @Unique
    private static final ThreadLocal<Boolean> expedition$fromData = ThreadLocal.withInitial(() -> false);

    @Inject(method = "loadFromResource", at = @At("HEAD"))
    private void expedition$beginData(ResourceLocation id, CallbackInfoReturnable<java.util.Optional<StructureTemplate>> cir) {
        expedition$fromData.set(true);
    }

    @Inject(method = "loadFromResource", at = @At("RETURN"))
    private void expedition$endData(ResourceLocation id, CallbackInfoReturnable<java.util.Optional<StructureTemplate>> cir) {
        expedition$fromData.set(false);
    }

    @Inject(method = "readStructure(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;", at = @At("HEAD"))
    private void expedition$facades(CompoundTag tag, CallbackInfoReturnable<StructureTemplate> cir) {
        if (expedition$fromData.get()) {
            StructureFacades.processTemplate(tag);
        }
    }
}
