package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A Registrate keeps its mod's entries in a plain table until the registry event. Mods are built on several threads at
 * once, and an addon that touches Create's item classes while building fills Create's Registrate from its own thread
 * while Create fills it from another: a write got lost, the client came up without create:schematic and the server
 * refused it ("registries with unknown keys"). One entry at a time per Registrate.
 */
@Mixin(value = AbstractRegistrate.class, remap = false)
abstract class RegistrateEntryLockMixin {
    @WrapMethod(method = "accept")
    private RegistryEntry<?, ?> expedition$oneAtATime(String name, ResourceKey<?> type, Builder<?, ?, ?, ?> builder, NonNullSupplier<?> creator,
        NonNullFunction<?, ?> entryFactory, Operation<RegistryEntry<?, ?>> original) {
        synchronized (this) {
            return original.call(name, type, builder, creator, entryFactory);
        }
    }
}
