package io.github.joemama.loader.mixin

import org.objectweb.asm.tree.ClassNode

import java.io.InputStream
import java.net.URL

import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.service.IMixinServiceBootstrap
import org.spongepowered.asm.service.IGlobalPropertyService
import org.spongepowered.asm.service.MixinServiceAbstract
import org.spongepowered.asm.service.IClassProvider
import org.spongepowered.asm.service.IClassBytecodeProvider
import org.spongepowered.asm.service.ITransformerProvider
import org.spongepowered.asm.service.IClassTracker
import org.spongepowered.asm.service.IMixinAuditTrail
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.service.ITransformer
import org.spongepowered.asm.service.IPropertyKey
import org.spongepowered.asm.service.IMixinInternal
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.mixin.transformer.IMixinTransformer

import io.github.joemama.loader.ModLoader

class Mixin: MixinServiceAbstract(), IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
  companion object {
    lateinit var transformer: IMixinTransformer
    fun initMixins() {
      MixinBootstrap.init()
    }
  }

  // TODO: Change when we get a legit name
  override fun getName(): String = "ModLoader"
  override fun isValid(): Boolean = true
  override fun getClassProvider(): IClassProvider = this
  override fun getBytecodeProvider(): IClassBytecodeProvider = this
  override fun getTransformerProvider(): ITransformerProvider = this
  override fun getClassTracker(): IClassTracker = this
  override fun getAuditTrail(): IMixinAuditTrail? = null
  override fun getPlatformAgents(): Collection<String> = listOf("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault")
  override fun getPrimaryContainer(): IContainerHandle = ContainerHandleURI(ModLoader::class.java.protectionDomain.codeSource.location.toURI())
  override fun getResourceAsStream(name: String): InputStream = throw IllegalStateException()

  override fun getClassPath(): Array<out URL> = arrayOf()
  override fun findClass(name: String): Class<*>? = ModLoader.classLoader.findClass(name)
  override fun findClass(name: String, resolve: Boolean): Class<*>? = Class.forName(name, resolve, ModLoader.classLoader)
  override fun findAgentClass(name: String, resolve: Boolean): Class<*> = Class.forName(name, resolve, ModLoader::class.java.classLoader)
  override fun getClassNode(name: String): ClassNode = throw IllegalStateException("to forward from modloader")
  override fun getClassNode(name: String, resolve: Boolean): ClassNode = throw IllegalStateException("to forward from modloader")
  override fun getTransformers(): Collection<ITransformer> = listOf()
  override fun getDelegatedTransformers(): Collection<ITransformer> = listOf()
  override fun addTransformerExclusion(name: String) = Unit
  override fun registerInvalidClass(name: String) = Unit
  override fun isClassLoaded(name: String): Boolean = ModLoader.classLoader.isClassLoaded(name)
  override fun getClassRestrictions(name: String): String = ""

  override fun offer(internal: IMixinInternal) {
    if (internal is IMixinTransformerFactory) {
      Mixin.transformer = internal.createTransformer()
    }
  }
}

class MixinBootstrap: IMixinServiceBootstrap {
  override fun getName(): String = "ModLoaderBootstrap"
  override fun getServiceClassName(): String = "io.github.joemama.loader.mixin.Mixin"
  
  override fun bootstrap() = Unit
}

data class PropertyKey(val key: String): IPropertyKey

class GlobalPropertyService: IGlobalPropertyService {
  val props: MutableMap<String, Any?> = mutableMapOf()
  override fun resolveKey(key: String): IPropertyKey = PropertyKey(key)
  override fun <T>getProperty(key: IPropertyKey): T? = this.props[(key as PropertyKey).key] as T
  override fun <T>getProperty(key: IPropertyKey, default: T?): T? = this.getProperty(key) ?: default
  override fun getPropertyString(key: IPropertyKey, default: String): String = this.getProperty(key, default).toString()

  override fun setProperty(key: IPropertyKey, value: Any?) {
   this.props[(key as PropertyKey).key] = value
  }
}
