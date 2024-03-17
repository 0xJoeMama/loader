package io.github.joemama.loader

import io.github.joemama.loader.meta.ModDiscoverer
import org.slf4j.LoggerFactory

import java.net.URL
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Path
import java.lang.invoke.MethodType
import java.lang.invoke.MethodHandles

import io.github.joemama.loader.transformer.TransformingClassLoader
import io.github.joemama.loader.mixin.Mixin
import io.github.joemama.loader.mixin.MixinTransformation
import io.github.joemama.loader.transformer.Transformer
import org.slf4j.Logger

interface LoaderPluginEntrypoint {
    fun onLoaderInit()
}

data class GameJar(val jarLoc: Path) {
    private val absolutePath by lazy { this.jarLoc.toAbsolutePath() }
    private val jarUri: String by lazy {
        URI.create("jar:${this.absolutePath.toUri().toURL()}!/").toString()
    }

    fun getContentUrl(name: String): URL = URI.create(this.jarUri + name).toURL()
}

object ModLoader {
    val logger: Logger = LoggerFactory.getLogger(ModLoader.javaClass)
    private lateinit var modDir: String
    private lateinit var gameJarPath: String
    lateinit var discoverer: ModDiscoverer
        private set
    lateinit var gameJar: GameJar
        private set
    lateinit var classLoader: TransformingClassLoader
        private set
    lateinit var transformer: Transformer
        private set

    internal fun parseArgs(args: Array<String>): Array<String> {
        var gameJarPath: String? = null
        var modDir: String? = null
        val newArgs = mutableListOf<String>()
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
        this.logger.info("starting mod loader")
        this.discoverer = ModDiscoverer(this.modDir)
        this.gameJar = GameJar(Paths.get(this.gameJarPath))

        this.classLoader = TransformingClassLoader()

        this.transformer = Transformer()

        // TODO: Move to a loader plugin once we have those properly implemented
        Mixin.initMixins()
        this.transformer.registerInternal(MixinTransformation)

        this.callEntrypoint("loader_start", LoaderPluginEntrypoint::onLoaderInit)
    }

    fun start(owner: String, method: String, desc: String, params: Array<String>) {
        this.logger.info("starting game")
        this.logger.debug("target game jars: ${this.gameJarPath}")
        this.logger.debug("game args: ${params.contentToString()}")

        val mainClass = this.classLoader.loadClass(owner)
        val mainMethod = MethodHandles.lookup().findStatic(
            mainClass,
            method,
            MethodType.fromMethodDescriptorString(desc, null)
        )

        mainMethod.invokeExact(params)
    }

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
    ModLoader.start(
        owner = "net.minecraft.client.main.Main",
        method = "main",
        desc = "([Ljava/lang/String;)V",
        params = newArgs
    )
}
