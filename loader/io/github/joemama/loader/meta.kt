package io.github.joemama.loader.meta

import org.tomlj.TomlTable
import org.tomlj.Toml
import org.tomlj.TomlArray
import java.io.File
import java.util.jar.JarFile
import java.io.InputStream
import java.io.FileFilter
import java.nio.file.Paths

class IllegalJarException(msg: String): Exception(msg)

data class Mod(val jar: JarFile, val meta: ModMeta) {
  companion object {
   fun parse(file: File): Mod? = try {
      val modJar = JarFile(file)
      val modMeta = modJar.getJarEntry("mods.toml")
      val metaToml = Toml.parse(modJar.getInputStream(modMeta)!!)
      val meta = ModMeta.deserialize(metaToml) ?: throw IllegalJarException("Invalid mods.toml file")
      
      Mod(modJar, meta)
    } catch (e: Exception) {
      println("[ERROR] file ${file.name} could not be parsed as a mod file: ${e.message}")
      e.printStackTrace()
      null
    }
  }
}

class ModDiscoverer(val modDirPath: String) {
  val modDir = Paths.get(this.modDirPath).toFile()
  val mods: List<Mod>
  init {
    println("[INFO] mod discovery running in folder ${modDirPath}")
    modDir.mkdirs()
    this.mods = modDir.listFiles( FileFilter { !it.isDirectory() }).map { Mod.parse(it) }.filterNotNull()
    println("[INFO] discovered ${mods.size} mod files")
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

data class ModMeta(val name: String, val version: String, val description: String, val entrypoints: List<Entrypoint>, val modid: String, val transforms: List<Transform>) {
  companion object {
    fun deserialize(t: TomlTable): ModMeta? {
      val name: String = t.getString("name") ?: return null
      val version: String  = t.getString("version") ?: return null
      val description: String = t.getString("description") ?: return null
      val id: String = t.getString("modid") ?: return null
      val entrypoints = mutableListOf<Entrypoint>()
      val transforms = mutableListOf<Transform>()

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

      return ModMeta(
        name = name, 
        version = version, 
        description = description, 
        entrypoints = entrypoints,
        transforms = transforms,
        modid = id
      )
    }
  }
}
