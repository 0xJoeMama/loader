package io.github.joemama.loader.meta

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.tomlj.TomlTable
import org.tomlj.Toml
import org.tomlj.TomlArray

import java.io.File
import java.util.jar.JarFile
import java.io.InputStream
import java.io.FileFilter
import java.nio.file.Paths
import java.net.URL
import java.net.URI

internal val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)


class IllegalJarException(msg: String): Exception(msg)
// TODO: Add meta parse exceptions to all meta entries
class MetaParseException(msg: String): Exception(msg)

data class Mod(val jar: JarFile, val meta: ModMeta) {
  companion object {
   fun parse(file: File): Mod? = try {
      val modJar = JarFile(file)
      val modMeta = modJar.getJarEntry("mods.toml")
      val metaToml = modJar.getInputStream(modMeta)!!.use {
        Toml.parse(it)
      }
      val meta = ModMeta.deserialize(metaToml) ?: throw IllegalJarException("Invalid mods.toml file")
      
      Mod(modJar, meta)
    } catch (e: Exception) {
      logger.error("file ${file.name} could not be parsed as a mod file: ${e.message}")
      e.printStackTrace()
      null
    }
  }

  val path by lazy {
    Paths.get(jar.name).toAbsolutePath()
  } 
  val url by lazy {
      URI("jar:" + this.path.toUri().toString() + "!/").toURL()
  }
}

class ModDiscoverer(val modDirPath: String) {
  val modDir = Paths.get(this.modDirPath).toFile()
  val mods: List<Mod>
  init {
    logger.info("mod discovery running in folder ${modDirPath}")
    modDir.mkdirs()
    this.mods = modDir.listFiles( FileFilter { !it.isDirectory() }).map { Mod.parse(it) }.filterNotNull()
    logger.info("discovered ${mods.size} mod files")
  }
}

data class Entrypoint(val id: String, val clazz: String) {
   companion object {
    fun deserialize(t: TomlTable): Entrypoint? {
      val id = t.getString("id") ?: return null
      val clazz = t.getString("class") ?: return null
      return Entrypoint(id, clazz)
    }
   }
}

data class Transform(val name: String, val target: String, val clazz: String) {
  companion object {
    fun deserialize(t: TomlTable): Transform? {
      val name = t.getString("name") ?: return null
      val target = t.getString("target") ?: return null
      val clazz = t.getString("class") ?: return null

      return Transform(name, target, clazz)
    }
  }
}

data class Mixin(val path: String) {
  companion object {
    fun deserialize(t: TomlTable): Mixin {
      val path = t.getString("path") ?: throw MetaParseException("must provide a \"path\" attribute for mixins")
      return Mixin(path)
    }
  }
}

data class ModMeta(val name: String, val version: String, val description: String, val entrypoints: List<Entrypoint>, val modid: String, val transforms: List<Transform>, val mixins: List<Mixin>) {
  companion object {
    fun deserialize(t: TomlTable): ModMeta? {
      val name: String = t.getString("name") ?: return null
      val version: String  = t.getString("version") ?: return null
      val description: String = t.getString("description") ?: return null
      val id: String = t.getString("modid") ?: return null
      val entrypoints = mutableListOf<Entrypoint>()
      val transforms = mutableListOf<Transform>()
      val mixins = mutableListOf<Mixin>()

      val entrypointsT = t.getArray("entrypoints")

      if (entrypointsT != null) {
        for (i in 0..<entrypointsT.size()) {
          entrypoints.add(Entrypoint.deserialize(entrypointsT.getTable(i)) ?: return null)
        }
      }

      val transformsT = t.getArray("transforms")
      if (transformsT != null) {
        for (i in 0..<transformsT.size()) {
          transforms.add(Transform.deserialize(transformsT.getTable(i)) ?: return null)
        }
      }

      val mixinsT = t.getArray("mixins")
      if (mixinsT != null) {
        for (i in 0..<mixinsT.size()) {
          mixins.add(Mixin.deserialize(mixinsT.getTable(i)))
        }
      }

      return ModMeta(
        name = name, 
        version = version, 
        description = description, 
        entrypoints = entrypoints,
        transforms = transforms,
        mixins = mixins,
        modid = id
      )
    }
  }
}
