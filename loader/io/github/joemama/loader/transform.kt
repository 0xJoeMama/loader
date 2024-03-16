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
   fun transform(clazz: ClassNode, name: String)
}

class Transformer(): ClassLoader(ClassLoader.getSystemClassLoader()) {
  val logger = LoggerFactory.getLogger(Transformer::class.java)

  fun getClassNode(name: String): ClassNode? {
    val normalName = name.replace(".", "/") + ".class"
    // getResourceAsStream since mixins require system resources as well
    return this.getResourceAsStream(normalName)?.use {
      val classReader = ClassReader(it)
      val classNode = ClassNode()
      classReader.accept(classNode, 0)
      classNode
    }
  }

  // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
  override public fun findClass(name: String): Class<*>? {
    synchronized (this.getClassLoadingLock(name)) {
      var classNode = this.getClassNode(name)

      // TODO; optimize the parsing of every loaded class
      if (classNode != null) {
        for (t in ModLoader.getTransforms(name)) {
          this.logger.info("transforming class $name")
          t.transform(classNode, name)
        }

        MixinTransform.transform(classNode, name)

        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(classWriter)
        // WARNING: Perhaps it might be a better idea to keep snapshots of the class in case someone messes up
        val classBytes = classWriter.toByteArray()

        return this.defineClass(name, classBytes, 0, classBytes!!.size)
      }

      return super.findClass(name)
    }
  }

  private fun tryResourceUrl(url: URL): URL? {
    try {
      val jarCon = url.openConnection() as JarURLConnection
      jarCon.getJarEntry()
      return url
    } catch (e: Exception) {
      return null
    }
  }

  override protected fun findResource(name: String): URL? {
    // first check if it's a game class
    var targetUrl = this.tryResourceUrl(ModLoader.gameJar.getContentUrl(name))
    if (targetUrl != null) return targetUrl
    
    // if not a game class, attempt to load it from mod jars
    for (mod in ModLoader.discoverer.mods) {
      targetUrl = this.tryResourceUrl(mod.getContentUrl(name))

      if (targetUrl != null) return targetUrl
    }

    // if no mod jars had it then it doesn't exist in us
    return null
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

