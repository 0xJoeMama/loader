package io.github.joemama.testmod

import io.github.joemama.loader.ModLoader

interface CommonEntrypoint {
    fun onInit()
}

@Suppress("unused")
fun apiInit() {
    ModLoader.callEntrypoint("common", CommonEntrypoint::onInit)
}

