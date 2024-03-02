package io.github.joemama.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
// import org.objectweb.asm.Opcodes
// import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ClassNode
// import org.objectweb.asm.tree.FieldInsnNode
// import org.objectweb.asm.tree.LdcInsnNode
// import org.objectweb.asm.tree.MethodInsnNode
// import org.objectweb.asm.tree.InsnList
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

interface Transform {
   val classTarget: String
   fun transform(clazz: ClassNode)
}

object RegistryTransformer: Transform {
  override val classTarget = "kd"
  override fun transform(clazz: ClassNode) {
    println("[INFO] Modifying BuiltInRegistries class")
  }
}

class Transformer(private val jarLoc: String, private val gameJar: JarFile): ClassLoader(ClassLoader.getSystemClassLoader()) {
  private val dataBuffer = ByteArray(1024)
  private val jarUrl: URL
  init {
    val p = Paths.get(jarLoc).toUri()
    this.jarUrl = URI("jar:" + p.toString() + "!/").toURL()
  }
    // we are given a class that parent loaders couldn't load. It's our turn to load it using the game_jar
  override protected fun findClass(name: String): Class<*> {
      val normalName = name.replace(".", "/") + (".class")
      val entry = this.gameJar.getJarEntry(normalName)
      if (entry != null) {
        var classBytes: ByteArray? = this.gameJar.getInputStream(entry).use {
          ByteArrayOutputStream(it.available()).use { out -> 
            var outBytes = it.read(this.dataBuffer)
            while (outBytes > 0) {
              out.write(this.dataBuffer, 0, outBytes)
              outBytes = it.read(this.dataBuffer)
            }

           out.toByteArray()
          }
        }

        if (classBytes != null) {
          // BuiltInRegistries
          if (name == "kd") {
            println("[TRANSFORMER] Transforming registry class")
            val classReader = ClassReader(classBytes)
            val classNode = ClassNode()
            classReader.accept(classNode, 0)
            RegistryTransformer.transform(classNode)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            classNode.accept(classWriter)
            classBytes = classWriter.toByteArray()
          }
          return this.defineClass(name, classBytes, 0, classBytes!!.size)
        }
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
  println("[DEBUG] target jars: ${args[0]}")
  val mainClass = loader.loadClass("net.minecraft.client.main.Main")
  //=========================================================================================
  //==============WARNING: Anything after this needs not use any of the transformable classes
  //==================== Here be dragons! ==================================================
  //=========================================================================================
  val t = Thread {
    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
    mainMethod.invoke(null, arrayOf("--version", "JoesCraft", "--accessToken", "69420", "--gameDir", "run"))
  }
  t.setContextClassLoader(loader)
  t.start()
  t.join()
}
