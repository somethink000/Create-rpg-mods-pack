package net.expedition.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tterrag.registrate.AbstractRegistrate;
import java.util.function.Consumer;
import net.neoforged.bus.api.EventPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Registrate keeps the listeners a mod adds before it has its event bus in plain static tables, and Azimuth's fix
 * (RegistrateOTERFix) adds a plain HashSet of the mods already flushed. Mods are built on several threads at once, so
 * on a many-core machine a write got lost and a mod's waiting listeners were flushed twice: Create Goggles then made
 * its creategoggles:item_modifier registry twice and loading broke. One mod at a time here, Azimuth's code included.
 */
@Mixin(targets = "com.tterrag.registrate.util.OneTimeEventReceiver", remap = false)
abstract class RegistrateListenerLockMixin {
    @Unique
    private static final Object expedition$lock = new Object();

    @WrapMethod(method = "addModListener(Lcom/tterrag/registrate/AbstractRegistrate;Lnet/neoforged/bus/api/EventPriority;Ljava/lang/Class;Ljava/util/function/Consumer;)V")
    private static void expedition$oneAtATime(AbstractRegistrate<?> owner, EventPriority priority, Class<?> evtClass, Consumer<?> listener, Operation<Void> original) {
        synchronized (expedition$lock) {
            original.call(owner, priority, evtClass, listener);
        }
    }
}
