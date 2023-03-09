package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.extensions.ExtensionPointName

interface ResolvedDependenciesProvider {

    companion object {

        private val extensionPointName
            get() = ExtensionPointName.create<ResolvedDependenciesProvider>("com.intellij.packagesearch.resolvedDependenciesProvider")

        fun getProvidersMap() = extensionPointName.extensions.asSequence()
            .flatMap { provider -> provider.supportedBuildSystems.map { it to provider } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    }

    val supportedBuildSystems: Set<BuildSystemType>

    fun resolvedDependencies(module: PackageSearchModule): List<UnifiedDependency>
}