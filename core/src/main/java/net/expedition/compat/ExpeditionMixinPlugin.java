package net.expedition.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Sable wraps every Particle.move and allocates a fresh hash set per call before it even looks for physics ships: with
 * campfire smoke around that was half of all garbage the client made (JFR), i.e. GC pauses every 2-3 s. After Sable's
 * wrapper is merged into Particle (ParticleSablePoolMixin runs later), {@code new ObjectOpenHashSet()} in it is replaced by
 * a reused, cleared per-thread set. If Sable changes and the pattern is gone, nothing is patched.
 */
public final class ExpeditionMixinPlugin implements IMixinConfigPlugin {
    private static final String SET = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet";

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("ParticleSablePoolMixin")) {
            return;
        }
        for (MethodNode method : targetClass.methods) {
            if (!method.name.contains("sable$moveWithSubLevels")) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(SET)
                    && type.getNext() != null && type.getNext().getOpcode() == Opcodes.DUP
                    && type.getNext().getNext() instanceof MethodInsnNode init && init.getOpcode() == Opcodes.INVOKESPECIAL
                    && init.owner.equals(SET) && init.name.equals("<init>") && init.desc.equals("()V")) {
                    MethodInsnNode reused = new MethodInsnNode(Opcodes.INVOKESTATIC, "net/expedition/compat/ParticleSets", "reused", "()L" + SET + ";", false);
                    method.instructions.insert(init, reused);
                    method.instructions.remove(type.getNext());
                    method.instructions.remove(init);
                    method.instructions.remove(type);
                    insn = reused;
                }
            }
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
