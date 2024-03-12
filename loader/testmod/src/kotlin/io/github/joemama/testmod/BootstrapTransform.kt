package io.github.joemama.testmod

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.AbstractInsnNode

import io.github.joemama.loader.transformer.Transform

class BootstrapTransform(): Transform {
  private val logger = LoggerFactory.getLogger(BootstrapTransform::class.java)
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
  override fun transform(clazz: ClassNode) {
    clazz.methods.find { it -> it.name == "bootStrap" && it.desc == "()V" }?.let { mn ->
      this.logger.info("modifying method ${mn.name}${mn.desc}")
      mn.instructions.find { insn -> 
        if (insn.type != AbstractInsnNode.METHOD_INSN) {
          false
        } else {
          val mIns = insn as MethodInsnNode
            mIns.owner == "net/minecraft/core/registries/BuiltInRegistries" && mIns.name == "bootStrap" && mIns.desc == "()V"
        }
      }?.let {
        val methodCall = MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/joemama/testmod/ApiInitKt", "apiInit", "()V")
        mn.instructions.insertBefore(it, methodCall)
        this.logger.debug("injected main call")
      }
    }
  }
}
