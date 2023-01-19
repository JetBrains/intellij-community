package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ResolvedDependenciesProvider
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenResolvedDependenciesProvider : ResolvedDependenciesProvider {

    override val supportedBuildSystems: Set<BuildSystemType>
        get() = setOf(BuildSystemType.MAVEN)

    override fun resolvedDependencies(module: PackageSearchModule): List<UnifiedDependency> =
        MavenProjectsManager.getInstance(module.nativeModule.project)
            .findProject(module.nativeModule)
            ?.dependencies
            ?.map { UnifiedDependency(it.groupId, it.artifactId, it.version, it.scope) }
            ?: emptyList()

}