package io.github.joemama.loader.mixin

import org.objectweb.asm.tree.ClassNode

import java.io.InputStream
import java.net.URL

import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.service.*
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.MixinEnvironment.Phase
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import org.spongepowered.asm.logging.LoggerAdapterDefault
import org.spongepowered.asm.logging.ILogger
import org.spongepowered.asm.util.ReEntranceLock

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.Transform

class Mixin : IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    companion object {
        // beware of local global property
        internal lateinit var transformer: IMixinTransformer
        internal lateinit var environment: MixinEnvironment
        fun initMixins() {
            // initialize mixins
            MixinBootstrap.init()

            // pass in configs
            for (cfg in ModLoader.discoverer.mods.flatMap { it.meta.mixins }.map { it.path }) {
                Mixins.addConfiguration(cfg)
            }

            // move to the default phase
            environment = MixinEnvironment.getEnvironment(Phase.DEFAULT)
        }
    }

    private val lock = ReEntranceLock(1)

    // TODO: Change when we get a legit name
    override fun getName(): String = "ModLoader"

    // TODO: change once we get legit side handling
    override fun getSideName(): String = "CLIENT"
    override fun isValid(): Boolean = true
    override fun getClassProvider(): IClassProvider = this
    override fun getBytecodeProvider(): IClassBytecodeProvider = this
    override fun getTransformerProvider(): ITransformerProvider = this
    override fun getClassTracker(): IClassTracker = this
    override fun getAuditTrail(): IMixinAuditTrail? = null
    override fun getPlatformAgents(): Collection<String> =
        listOf("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault")

    override fun getPrimaryContainer(): IContainerHandle =
        ContainerHandleURI(ModLoader::class.java.protectionDomain.codeSource.location.toURI())

    override fun getResourceAsStream(name: String): InputStream? = ModLoader.classLoader.getResourceAsStream(name)
    override fun prepare() = Unit
    override fun getInitialPhase(): Phase = Phase.PREINIT
    override fun init() = Unit
    override fun beginPhase() = Unit
    override fun checkEnv(o: Any) = Unit
    override fun getReEntranceLock(): ReEntranceLock = this.lock
    override fun getMixinContainers(): Collection<IContainerHandle> = listOf()
    override fun getMinCompatibilityLevel(): MixinEnvironment.CompatibilityLevel =
        MixinEnvironment.CompatibilityLevel.JAVA_8

    override fun getMaxCompatibilityLevel(): MixinEnvironment.CompatibilityLevel =
        MixinEnvironment.CompatibilityLevel.JAVA_17

    override fun getLogger(name: String): ILogger = LoggerAdapterDefault(name)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java")
    override fun getClassPath(): Array<out URL> = arrayOf()
    override fun findClass(name: String): Class<*>? = ModLoader.classLoader.findClass(name)
    override fun findClass(name: String, resolve: Boolean): Class<*>? =
        Class.forName(name, resolve, ModLoader.classLoader)

    override fun findAgentClass(name: String, resolve: Boolean): Class<*> =
        Class.forName(name, resolve, ModLoader::class.java.classLoader)

    override fun getClassNode(name: String): ClassNode? = this.getClassNode(name, true)

    // runTransformers means nothing in our case since we always run transformers before Mixin application
    override fun getClassNode(name: String, runTransformers: Boolean): ClassNode? =
        ModLoader.classLoader.getClassNode(name)

    override fun getTransformers(): Collection<ITransformer> = listOf()
    override fun getDelegatedTransformers(): Collection<ITransformer> = listOf()
    override fun addTransformerExclusion(name: String) = Unit
    override fun registerInvalidClass(name: String) = Unit
    override fun isClassLoaded(name: String): Boolean = ModLoader.classLoader.isClassLoaded(name)
    override fun getClassRestrictions(name: String): String = ""

    override fun offer(internal: IMixinInternal) {
        if (internal is IMixinTransformerFactory) {
            transformer = internal.createTransformer()
        }
    }
}

object MixinTransform : Transform {
    override fun transform(clazz: ClassNode, name: String) {
        if (Mixin.transformer.transformClass(Mixin.environment, name, clazz)) {
            ModLoader.logger.debug("transformed {} with mixin", clazz.name)
        }
    }
}

class MixinBootstrap : IMixinServiceBootstrap {
    override fun getName(): String = "ModLoaderBootstrap"
    override fun getServiceClassName(): String = "io.github.joemama.loader.mixin.Mixin"
    override fun bootstrap() = Unit
}

data class PropertyKey(val key: String) : IPropertyKey

class GlobalPropertyService : IGlobalPropertyService {
    private val props: MutableMap<String, Any?> = mutableMapOf()
    override fun resolveKey(key: String): IPropertyKey = PropertyKey(key)

    // safe since we trust mixins to keep types properly stored
    @Suppress("unchecked_cast")
    override fun <T> getProperty(key: IPropertyKey): T? = this.props[(key as PropertyKey).key] as T
    override fun <T> getProperty(key: IPropertyKey, default: T?): T? = this.getProperty(key) ?: default
    override fun getPropertyString(key: IPropertyKey, default: String): String =
        this.getProperty(key, default).toString()

    override fun setProperty(key: IPropertyKey, value: Any?) {
        this.props[(key as PropertyKey).key] = value
    }
}
