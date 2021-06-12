package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.util.registry.Registry

object FeatureFlags {

    val useDebugLogging: Boolean
        get() = Registry.`is`("packagesearch.plugin.debug.logging", false)

    val showRepositoriesTab: Boolean
        get() = Registry.`is`("packagesearch.plugin.repositories.tab", false)
}
