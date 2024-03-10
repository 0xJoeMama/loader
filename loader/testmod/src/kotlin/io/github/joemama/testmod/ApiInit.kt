package io.github.joemama.testmod

import io.github.joemama.loader.ModLoader

interface CommonEntrypoint {
  fun onInit()
}

fun apiInit() {
  println("[INFO] calling main entrypoint")
  ModLoader.callEntrypoint<CommonEntrypoint>("common") {
    it.onInit()
  }
}

