package io.github.joemama.loader.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.net.URL
import java.net.URI
import java.nio.file.Paths
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.JarURLConnection
import java.util.Enumeration
import java.util.Collections
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.io.OutputStream
import java.util.jar.JarFile

import io.github.joemama.loader.ModLoader

interface Transform {
   fun transform(clazz: ClassNode)
}

class Transformer(): ClassLoader(ClassLoader.getSystemClassLoader()) {
  private val jarLoc: String = ModLoader.gameJarPath
  private val jarUrl: URL

  init {
    val p = Paths.get(jarLoc).toUri()
    this.jarUrl = URI("jar:" + p.toString() + "!/").toURL()
  }

  private fun getClassFromJar(jf: JarFile, name: String): ByteArray? {
    val entry = jf.getJarEntry(name)
    if (entry == null) return null
    return jf.getInputStream(entry)!!.use {
      ByteArrayOutputStream(it.available()).use { res ->
        var b: Int = it.read()
          while (b != -1) {
            res.write(b)
              b = it.read()
          }

        res.toByteArray()
      }
    }
  }

  private fun getGameClass(name: String): ByteArray? = this.getClassFromJar(ModLoader.gameJar, name)

  private fun getModClass(name: String): ByteArray? {
    for (m in ModLoader.discoverer.mods) {
      println("[DEBUG] checking mod file ${m.jar.name} for class $name")
      val bytes = this.getClassFromJar(m.jar, name)
      if (bytes != null) return bytes
    }

    return null
  }

  // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
  override protected fun findClass(name: String): Class<*>? {
    synchronized (this.getClassLoadingLock(name)) {
      val normalName = name.replace(".", "/") + ".class"
      // TODO: check all mc package names
      var classBytes = if (normalName.startsWith("net/minecraft/") || normalName.startsWith("com/mojang/")) {
        this.getGameClass(normalName)
      } else {
        this.getModClass(normalName)
      }

      if (classBytes != null) {
        for (t in ModLoader.getTransforms(name)) {
          println("[TRANSFORMER] Transforming class $name")
          val classReader = ClassReader(classBytes)
          val classNode = ClassNode()
          classReader.accept(classNode, ClassReader.EXPAND_FRAMES)
          t.transform(classNode)
          val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
          classNode.accept(classWriter)
          // WARNING: Perhaps it might be a better idea to keep snapshots of the class in case someone messes up
          classBytes = classWriter.toByteArray()
        }
        return this.defineClass(name, classBytes, 0, classBytes!!.size)
      }

      return super.findClass(name)
    }
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

  companion object {
    init {
      registerAsParallelCapable()
    }
  }
}

