package io.github.joemama.testmod.mixins;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.main.Main.class)
public class ExampleMixin {
  @Inject(method = "main", at = @At("HEAD"))
  private static void testmod$onMain(String[] args, CallbackInfo ci) {
    System.out.println("Hello from mixins");
  }
}
