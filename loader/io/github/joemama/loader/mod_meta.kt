package io.github.joemama.loader

import org.tomlj.TomlTable
import org.tomlj.TomlArray

data class Entrypoint(val id: String, val clazz: String) {
   companion object {
    fun deserialize(t: TomlTable): Entrypoint? {
      val id = t.getString("id") ?: return null
      val clazz = t.getString("class") ?: return null
      return Entrypoint(id, clazz)
    }
   }
}

data class Mod(val name: String, val version: String, val description: String, val entrypoints: Array<Entrypoint>) {
  companion object {
    fun deserialize(t: TomlTable): Mod? {
      val name: String = t.getString("name") ?: return null
      val version: String  = t.getString("version") ?: return null
      val description: String = t.getString("description") ?: return null

      val entrypointsT: TomlArray = t.getArray("entrypoints") ?: return null
      val entrypoints = mutableListOf<Entrypoint>()
      for (i in 0..entrypointsT.size()) {
        // TODO: give error message
        entrypoints.add(Entrypoint.deserialize(entrypointsT.getTable(0)) ?: return null)
      }

      return Mod(name, version, description, entrypoints.toTypedArray())
    }
  }
}
