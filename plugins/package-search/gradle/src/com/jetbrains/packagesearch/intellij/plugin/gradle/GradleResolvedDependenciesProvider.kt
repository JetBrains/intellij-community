package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.components.service
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ResolvedDependenciesProvider

class GradleResolvedDependenciesProvider : ResolvedDependenciesProvider {

    override val supportedBuildSystems
        get() = setOf(BuildSystemType.GRADLE_CONTAINER, BuildSystemType.GRADLE_KOTLIN, BuildSystemType.GRADLE_GROOVY)

    override fun resolvedDependencies(module: PackageSearchModule) =
        module.nativeModule.project
            .service<GradleConfigurationReportNodeProcessor.Cache>()
            .state[module.projectDir.absolutePath]
            ?.configurations
            ?.flatMap { configuration ->
                configuration.dependencies.map { UnifiedDependency(it.groupId, it.artifactId, it.version, configuration.name) }
            }
            ?.toList()
            ?: emptyList()
}