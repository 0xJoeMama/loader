package io.github.joemama.testmod

import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries

import io.github.joemama.testmod.CommonEntrypoint

class Test: CommonEntrypoint {
  val A = Item(Item.Properties())
  override fun onInit() {
    Registry.register(BuiltInRegistries.ITEM, ResourceLocation("mymod", "test_item"), A)
  }
}
