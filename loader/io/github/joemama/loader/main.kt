package io.github.joemama.loader

import java.nio.file.StandardOpenOption
import java.nio.file.Path
import java.nio.file.Files

object Transformer: ClassLoader() {
  val delegate = ClassLoader.getSystemClassLoader()
  override fun loadClass(name: String): Class<*> { 
    return delegate.loadClass(name)
  }
  init {
    registerAsParallelCapable()
  }
}

fun main() {
  val mainClass = Transformer.loadClass("net.minecraft.client.main.Main")
  val t = Thread {
    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
    mainMethod.invoke(null, arrayOf("--version", "JoesCraft", "--accessToken", "69420", "--gameDir", "run"))
  }
  t.setContextClassLoader(Transformer)
  t.start()
  t.join()
}
