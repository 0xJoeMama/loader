package io.github.joemama.testmod

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

class BootstrapTransformation : Transformation {
    // ======================== Code from Bootstrap=======================
    // public static void bootStrap() {
    //     if (isBootstrapped) {
    //         return;
    //     }
    //     isBootstrapped = true;
    //     Instant $$0 = Instant.now();
    //     if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
    //         throw new IllegalStateException("Unable to load registries");
    //     }
    //     FireBlock.bootStrap();
    //     ComposterBlock.bootStrap();
    //     if (EntityType.getKey(EntityType.PLAYER) == null) {
    //         throw new IllegalStateException("Failed loading EntityTypes");
    //     }
    //     PotionBrewing.bootStrap();
    //     EntitySelectorOptions.bootStrap();
    //     DispenseItemBehavior.bootStrap();
    //     CauldronInteraction.bootStrap();
    //     ================= Our Code ============================================
    //     ApiInitKt.apiInit();
    //     =======================================================================
    //     BuiltInRegistries.bootStrap();
    //     CreativeModeTabs.validate();
    //     Bootstrap.wrapStreams();
    //     bootstrapDuration.set(Duration.between((Temporal)$$0, (Temporal)Instant.now()).toMillis());
    // }
    override fun transform(clazz: ClassNode, name: String) {
        clazz.methods.find { it.name == "bootStrap" && it.desc == "()V" }?.let { mn ->
            mn.instructions.find { insn ->
                if (insn.type != AbstractInsnNode.METHOD_INSN) {
                    false
                } else {
                    val mIns = insn as MethodInsnNode
                    mIns.owner == "net/minecraft/core/registries/BuiltInRegistries" && mIns.name == "bootStrap" && mIns.desc == "()V"
                }
            }?.let {
                val methodCall = MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "io/github/joemama/testmod/ApiInit",
                    "apiInit",
                    "()V"
                )
                mn.instructions.insertBefore(it, methodCall)
                ModLoader.transformer.logger.debug("injected API main call")
            }
        }
    }
}
