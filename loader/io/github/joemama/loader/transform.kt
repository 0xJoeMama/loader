package io.github.joemama.loader.transformer

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
import io.github.joemama.loader.mixin.Mixin
import io.github.joemama.loader.mixin.MixinTransform

interface Transform {
   fun transform(clazz: ClassNode)
}

class Transformer(): ClassLoader(ClassLoader.getSystemClassLoader()) {
  val logger = LoggerFactory.getLogger(Transformer::class.java)
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
      val bytes = this.getClassFromJar(m.jar, name)
      if (bytes != null) return bytes
    }

    return null
  }

  // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
  override public fun findClass(name: String): Class<*>? {
    synchronized (this.getClassLoadingLock(name)) {
      val normalName = name.replace(".", "/") + ".class"
      // TODO: check all mc package names
      var classBytes = if (normalName.startsWith("net/minecraft/") || normalName.startsWith("com/mojang/")) {
        this.getGameClass(normalName)
      } else {
        this.getModClass(normalName)
      }

      // TODO; optimize the parsing of every loaded class
      if (classBytes != null) {
        val classReader = ClassReader(classBytes)
        val classNode = ClassNode()
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES)

        MixinTransform.transform(classNode)

        for (t in ModLoader.getTransforms(name)) {
          this.logger.info("Transforming class $name")
          t.transform(classNode)
        }

        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(classWriter)
        // WARNING: Perhaps it might be a better idea to keep snapshots of the class in case someone messes up
        classBytes = classWriter.toByteArray()

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

  fun isClassLoaded(name: String): Boolean = synchronized(this.getClassLoadingLock(name)) { this.findLoadedClass(name) != null }

  companion object {
    init {
      registerAsParallelCapable()
    }
  }
}

