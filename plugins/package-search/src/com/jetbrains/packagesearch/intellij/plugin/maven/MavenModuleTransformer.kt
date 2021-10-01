package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager

private class MavenModuleTransformer : ModuleTransformer {

    override fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        nativeModules.mapNotNull { nativeModule ->
            runCatching {
                MavenProjectsManager.getInstance(project).findProject(nativeModule)
            }.getOrNull()?.let {
                createMavenProjectModule(project, nativeModule, it)
            }
        }

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
        )
    }
}
