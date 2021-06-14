package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.NavigatableDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.mavenProjectsManager
import com.jetbrains.packagesearch.intellij.plugin.util.tryFindProjectOrNull
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenModuleTransformer : ModuleTransformer {

    override fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        nativeModules.mapNotNull { nativeModule ->
            project.mavenProjectsManager.tryFindProjectOrNull(nativeModule)?.let { mavenProject ->
                ProjectModule(
                    name = mavenProject.name ?: nativeModule.name,
                    nativeModule = nativeModule,
                    parent = null,
                    buildFile = mavenProject.file,
                    buildSystemType = BuildSystemType.MAVEN,
                    moduleType = MavenProjectModuleType,
                    navigatableDependency = createNavigatableDependencyCallback(project, mavenProject)
                )
            }
        }

    companion object {

        private fun createNavigatableDependencyCallback(project: Project, mavenProject: MavenProject): NavigatableDependency =
            { groupId: String, artifactId: String, _: PackageVersion ->
                val dependencyElement = mavenProject.findDependencies(groupId, artifactId)
                    .firstOrNull()

                if (dependencyElement != null) {
                    MavenNavigationUtil.createNavigatableForDependency(
                        project,
                        mavenProject.file,
                        dependencyElement
                    )
                } else {
                    null
                }
            }
    }
}
