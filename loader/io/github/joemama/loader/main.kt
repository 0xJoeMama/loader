package io.github.joemama.loader

import org.tomlj.Toml
import java.util.jar.JarFile
import io.github.joemama.loader.meta.ModDiscoverer
import io.github.joemama.loader.transformer.Transformer
import io.github.joemama.loader.transformer.Transform
import io.github.joemama.loader.mixin.Mixin

object ModLoader {
  lateinit var modDir: String
  lateinit var gameJarPath: String
  lateinit var discoverer: ModDiscoverer

  lateinit var gameJar: JarFile
  lateinit var classLoader: Transformer 

  internal fun parseArgs(args: Array<String>): Array<String> {
    var gameJarPath: String? = null
    var modDir: String? = null
    val newArgs= mutableListOf<String>()
    var i = 0
    while (i < args.size) {
      when (args[i]) {
        "--print-cp" -> {
          val cp = System.getProperty("java.class.path").split(":")
            for (s in cp) {
              println(s)
            }
        }
        "--source" -> {
          if (i + 1 > args.size - 1) throw IllegalArgumentException("Expected a jar file location to be provided")
          gameJarPath = args[i + 1]
          i++
        }
        "--mods" -> {
          if (i + 1 > args.size - 1) throw IllegalArgumentException("Expected a mod directory to be provided")
          modDir = args[i + 1]
          i++
        }
        else -> {
          newArgs.add(args[i])
        }
      }
      i++
    }

    this.gameJarPath = gameJarPath ?: throw IllegalArgumentException("Provide a source jar with --source")
    this.modDir = modDir ?: throw IllegalArgumentException("Provide a mod directory with --mods")
    return newArgs.toTypedArray()
  }

  internal fun initLoader() {
    println("[INFO] starting mod loader")
    this.discoverer = ModDiscoverer(this.modDir)
    this.gameJar = JarFile(this.gameJarPath)
    this.classLoader = Transformer()

    Mixin.initMixins()
  }

  fun start(owner: String, method: String, params: Array<String>) {
    println("[INFO] starting game")
    println("[DEBUG] target game jars: ${this.gameJarPath}")
    println("[DEBUG] game args: ${params.contentToString()}")
    //=========================================================================================
    //============= WARNING: Anything after this needs not use any of the transformable classes
    //====================               Here be dragons!                  ====================
    //=========================================================================================
    val mainClass = this.classLoader.loadClass(owner)
    val t = Thread {
      val mainMethod = mainClass.getMethod(method, Array<String>::class.java)
      mainMethod.invoke(null, params)
    }
    t.setContextClassLoader(this.classLoader)
    t.start()
    t.join()
  }

  fun getTransforms(clazz: String): List<Transform> = this.discoverer.mods
      .flatMap { it.meta.transforms }
      .filter { it.target == clazz }
      .map { Class.forName(it.clazz, true, this.classLoader).getDeclaredConstructor().newInstance() as Transform }

  inline fun <reified T> callEntrypoint(id: String, crossinline method: (T) -> Unit) {
     this.discoverer.mods
          .flatMap { it.meta.entrypoints }
          .filter { it.id == id }
          .map { Class.forName(it.clazz, true, this.classLoader).getDeclaredConstructor().newInstance() as T }
          .forEach { method(it) }
  }

}

fun main(args: Array<String>) {
  val newArgs = ModLoader.parseArgs(args)
  ModLoader.initLoader()
  ModLoader.start(owner = "net.minecraft.client.main.Main", method = "main", params = newArgs)
}
