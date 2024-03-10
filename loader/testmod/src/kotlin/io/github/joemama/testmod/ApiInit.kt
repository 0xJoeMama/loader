package io.github.joemama.testmod

import io.github.joemama.loader.ModLoader

interface CommonEntrypoint {
  fun onInit()
}

fun apiInit() {
  ModLoader.callEntrypoint<CommonEntrypoint>("common") {
    it.onInit()
  }
}

