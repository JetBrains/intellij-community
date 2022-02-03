package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager

internal class MavenModuleTransformer : CoroutineModuleTransformer {

    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        nativeModules.parallelMap { nativeModule ->
            runCatching { readAction { MavenProjectsManager.getInstance(project).findProject(nativeModule) } }
                .onFailure {
                    logDebug(contextName = "MavenModuleTransformer", it) { "Error finding Maven module ${nativeModule.name}" }
                }
                .getOrNull()?.let {
                    createMavenProjectModule(project, nativeModule, it)
                }
        }.filterNotNull()

    private fun createMavenProjectModule(
        project: Project,
        nativeModule: Module,
        mavenProject: MavenProject
    ): ProjectModule {
        val buildFile = mavenProject.file
        return ProjectModule(
            name = mavenProject.name ?: nativeModule.name,
            nativeModule = nativeModule,
            parent = null,
            buildFile = buildFile,
            buildSystemType = BuildSystemType.MAVEN,
            moduleType = MavenProjectModuleType,
            navigatableDependency = { groupId, artifactId, _ ->
                mavenProject.findDependencies(groupId, artifactId).firstOrNull()?.let {
                    MavenNavigationUtil.createNavigatableForDependency(project, buildFile, it)
                }
            },
            availableScopes = PackageSearchMavenConfiguration.getInstance(project).getMavenScopes(),
        )
    }
}
