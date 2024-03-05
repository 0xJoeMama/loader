package io.github.joemama.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.AbstractInsnNode
import java.net.URL
import java.net.URI
import java.nio.file.Paths
import java.util.jar.JarFile
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.JarURLConnection
import java.util.Enumeration
import java.util.Collections
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.io.OutputStream

interface Transform {
   val classTarget: String
   val name: String
   fun transform(clazz: ClassNode)
}

object BuiltInRegistriesTransform: Transform {
  override val classTarget = "net.minecraft.server.Bootstrap"
  override val name = "bootstrap transform for entrypoints"

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
    //     LoaderKt.loaderInit();                                               ||
    //     =======================================================================
    //     BuiltInRegistries.bootStrap();
    //     CreativeModeTabs.validate();
    //     Bootstrap.wrapStreams();
    //     bootstrapDuration.set(Duration.between((Temporal)$$0, (Temporal)Instant.now()).toMillis());
    // }
  override fun transform(clazz: ClassNode) {
    clazz.methods.find { it -> it.name == "bootStrap" && it.desc == "()V" }?.let { mn ->
      println("[DEBUG] modifying method ${mn.name}${mn.desc}")
      mn.instructions.find { insn -> 
        if (insn.type != AbstractInsnNode.METHOD_INSN) {
          false
        } else {
          val mIns = insn as MethodInsnNode
            mIns.owner == "net/minecraft/core/registries/BuiltInRegistries" && mIns.name == "bootStrap" && mIns.desc == "()V"
        }
      }?.let {
        val methodCall = MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/joemama/loader/entrypoint/LoaderKt", "loaderInit", "()V")
        mn.instructions.insertBefore(it, methodCall)
        println("[DEBUG] injected main call")
      }
    }
  }
}

class Transformer(private val jarLoc: String, private val gameJar: JarFile): ClassLoader(ClassLoader.getSystemClassLoader()) {
  private val transforms = listOf(
    BuiltInRegistriesTransform
  )
  private val jarUrl: URL

  init {
    val p = Paths.get(jarLoc).toUri()
    this.jarUrl = URI("jar:" + p.toString() + "!/").toURL()
  }

  // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
  override protected fun findClass(name: String): Class<*>? {
    val normalName = name.replace(".", "/") + (".class")
    val entry = this.gameJar.getJarEntry(normalName)
    if (entry == null) return super.findClass(name);
    var classBytes: ByteArray? = this.gameJar.getInputStream(entry).use {
      ByteArrayOutputStream(it.available()).use { res ->
        var b: Int = it.read()
          while (b != -1) {
            res.write(b)
              b = it.read()
          }

        res.toByteArray()
      }
    }

    if (classBytes != null) {
      for (t in this.transforms) {
        if (t.classTarget == name) {
          println("[TRANSFORMER] Transforming class $name using ${t.name}")
            val classReader = ClassReader(classBytes)
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES)
            t.transform(classNode)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            classNode.accept(classWriter)
            // WARNING: Perhaps it might be a better idea to keep snapshots of the class in case someone messes up
            classBytes = classWriter.toByteArray()
        }
      }
      return this.defineClass(name, classBytes, 0, classBytes!!.size)
    }

    return super.findClass(name)
  }

  override protected fun findResource(name: String): URL? {
    val targetUrl = URI(this.jarUrl.toString() + name).toURL()
    val jarCon = targetUrl.openConnection() as JarURLConnection
    try {
      jarCon.getJarEntry()
      return targetUrl
    } catch (e: IOException) {
      return null
    }
  }

  override protected fun findResources(name: String): Enumeration<URL> {
    val res = this.findResource(name)
    return if (res == null) {
      Collections.emptyEnumeration()
    } else {
      // we can guarantee there's only gonna be one file because of obfuscation
      Collections.enumeration(listOf(res))
    }
  }
}

fun main(args: Array<String>) {
  println("[INFO] starting mod loader")
  if (args.contains("-print-cp")) {
    val cp = System.getProperty("java.class.path").split(":")
      for (s in cp) {
        println(s)
      }
  }
  val jarLoc = args[0]
  val jf = JarFile(jarLoc)
  val loader = Transformer(jarLoc, jf)

  println("[INFO] starting minecraft")
  println("[DEBUG] target game jars: ${args[0]}")
  //=========================================================================================
  //============= WARNING: Anything after this needs not use any of the transformable classes
  //====================               Here be dragons!                  ====================
  //=========================================================================================
  val mainClass = loader.loadClass("net.minecraft.client.main.Main")
  val t = Thread {
    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
    mainMethod.invoke(null, args.copyOfRange(1, args.size))
  }
  t.setContextClassLoader(loader)
  t.start()
  t.join()
}
