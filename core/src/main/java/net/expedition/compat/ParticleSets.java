package net.expedition.compat;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/** One cleared set per thread for Sable's per-particle sub-level lookup (see ExpeditionMixinPlugin). */
public final class ParticleSets {
    private ParticleSets() {
    }

    private static final ThreadLocal<ObjectOpenHashSet<Object>> SET = ThreadLocal.withInitial(ObjectOpenHashSet::new);

    public static ObjectOpenHashSet<Object> reused() {
        ObjectOpenHashSet<Object> set = SET.get();
        set.clear();
        return set;
    }
}
