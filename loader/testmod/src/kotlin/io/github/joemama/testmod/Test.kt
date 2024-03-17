package io.github.joemama.testmod

import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries

class Test: CommonEntrypoint {
  private val testItem = Item(Item.Properties())
  override fun onInit() {
    Registry.register(BuiltInRegistries.ITEM, ResourceLocation("mymod", "test_item"), testItem)
  }
}
