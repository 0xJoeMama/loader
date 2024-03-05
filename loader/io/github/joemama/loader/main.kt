package io.github.joemama.loader

import org.tomlj.Toml
import io.github.joemama.loader.Transformer
import java.util.jar.JarFile

fun main(args: Array<String>) {
  println("[INFO] starting mod loader")
  var jarLoc: String? = null
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
        jarLoc = args[i + 1]
        i++
      }
      else -> {
        newArgs.add(args[i])
      }
    }
    i++
  }

  jarLoc = jarLoc ?: throw IllegalArgumentException("Provide a source jar with --source")

  val toml = """
  name = "Mymod"
  version = "69"
  description = "Why hello there"

  [[entrypoints]]
  id = "client"
  class = "net.joe.mama.ClientClass"
  """
  val m = Mod.deserialize(Toml.parse(toml))
  println(m)

  return
  val jf = JarFile(jarLoc)
  val loader = Transformer(jarLoc, jf)

  println("[INFO] starting minecraft")
  println("[DEBUG] target game jars: ${jarLoc}")
  println("[DEBUG] game args: ${newArgs}")
  //=========================================================================================
  //============= WARNING: Anything after this needs not use any of the transformable classes
  //====================               Here be dragons!                  ====================
  //=========================================================================================
  val mainClass = loader.loadClass("net.minecraft.client.main.Main")
  val t = Thread {
    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
    mainMethod.invoke(null, newArgs.toTypedArray())
  }
  t.setContextClassLoader(loader)
  t.start()
  t.join()
}
