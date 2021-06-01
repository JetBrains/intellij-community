package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

internal class MavenProjectModuleProvider : ProjectModuleProvider {

    override fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule> =
        ModuleManager.getInstance(project).modules.asSequence()
            .flatMap { obtainProjectModulesFor(project, it) }
            .distinctBy { it.buildFile }

    private fun obtainProjectModulesFor(project: Project, module: Module): Sequence<ProjectModule> {
        if (!MavenUtil.isMavenModule(module)) {
            return emptySequence()
        }

        val mavenManager = MavenProjectsManager.getInstance(project)
        val mavenProject = mavenManager.findProject(module) ?: return emptySequence()

        return sequenceOf(
            ProjectModule(
                name = module.name,
                nativeModule = module,
                parent = null,
                buildFile = mavenProject.file,
                buildSystemType = BuildSystemType.MAVEN,
                moduleType = MavenProjectModuleType
            ).apply {
                getNavigatableDependency = createNavigatableDependencyCallback(project, mavenProject)
            }
        )
    }

    private fun createNavigatableDependencyCallback(project: Project, mavenProject: MavenProject) =
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
