package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.openapi.util.registry.Registry

object FeatureFlags {

    val mockRepositoriesApi: Boolean
        get() = Registry.`is`("packagesearch.repository.mock.api", false)
}
