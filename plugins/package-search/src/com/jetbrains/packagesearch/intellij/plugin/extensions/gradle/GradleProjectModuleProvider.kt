package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType.GRADLE_GROOVY
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleProvider
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.util.GradleConstants

private val logger by lazy { Logger.getInstance(GradleProjectModuleProvider::class.java) }

open class GradleProjectModuleProvider : ProjectModuleProvider {

    companion object {
        fun findDependencyElement(file: PsiFile, groupId: String, artifactId: String): PsiElement? {
            var index = file.text.indexOf(groupId)
            while (index >= 0) {
                PsiUtil.getElementAtOffset(file, index).apply {
                    if (text.contains(groupId) && text.contains(artifactId)) {
                        return this
                    }
                }
                index = file.text.indexOf(groupId, index + 1)
            }
            return null
        }
    }

    override fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule> = sequence {
        val modules = mutableListOf<ProjectModule>()
        for (module in ModuleManager.getInstance(project).modules) {
            val ourModule = obtainProjectModulesFor(project, module) ?: continue
            modules += ourModule
            yield(ourModule)
        }

        for (ourModule in modules) {
            yieldAll(getAllSubmodules(ourModule, project))
        }
    }.distinct()

    private fun createNavigatableDependencyCallback(project: Project, file: VirtualFile) = { groupId: String, artifactId: String, _: String ->
        PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
            val dependencyElement = findDependencyElement(psiFile, groupId, artifactId) ?: return@let null
            return@let dependencyElement as Navigatable
        }
    }

    private fun obtainProjectModulesFor(project: Project, module: Module): ProjectModule? {
        val externalRootProject = findExternalProject(project, module) ?: return null
        val buildFile = externalRootProject.buildFile ?: return null
        val buildVirtualFile = LocalFileSystem.getInstance().findFileByPath(buildFile.canonicalPath) ?: return null
        val nativeModule = ModuleUtilCore.findModuleForFile(buildVirtualFile, project) ?: return null

        return ProjectModule(
            name = externalRootProject.name,
            nativeModule = nativeModule,
            parent = null,
            buildFile = buildVirtualFile,
            buildSystemType = GRADLE_GROOVY,
            moduleType = GradleProjectModuleType
        )
            .apply {
                getNavigatableDependency = createNavigatableDependencyCallback(project, buildVirtualFile)
            }
    }

    private fun getAllSubmodules(rootModule: ProjectModule, project: Project): Sequence<ProjectModule> {
        val externalRootProject = findExternalProject(project, rootModule.nativeModule) ?: return emptySequence()

        val modules = mutableListOf(rootModule)
        externalRootProject.addChildrenToListRecursive(modules, rootModule, project)
        return modules.asSequence()
    }

    private fun findExternalProject(project: Project, module: Module): ExternalProject? {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
            return null
        }

        val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        if (rootProjectPath == null) {
            logger.debug("Root external project was not yet imported, project=${project.projectFilePath}, module=${module.moduleFilePath}")
            return null
        }

        val externalProjectDataCache = ExternalProjectDataCache.getInstance(project)
        val externalProject = externalProjectDataCache.getRootExternalProject(rootProjectPath)
        if (externalProject == null) {
            logger.debug("External project is not yet cached, project=${project.projectFilePath}, module=${module.moduleFilePath}")
            return null
        }

        return externalProject
    }

    private fun ExternalProject.addChildrenToListRecursive(
        modules: MutableList<ProjectModule>,
        currentModule: ProjectModule,
        project: Project
    ) {
        val localFileSystem = LocalFileSystem.getInstance()

        childProjects.values.forEach { externalProject ->
            // TODO: reuse code from the above
            // TODO: make it use sequence instead
            val projectBuildFile = externalProject.buildFile?.canonicalPath?.let(localFileSystem::findFileByPath)
                ?: return@forEach
            val nativeModule = ModuleUtilCore.findModuleForFile(projectBuildFile, project)
                ?: return@forEach

            val projectModule = ProjectModule(
                name = externalProject.name,
                nativeModule = nativeModule,
                parent = currentModule,
                buildFile = projectBuildFile,
                buildSystemType = GRADLE_GROOVY,
                moduleType = GradleProjectModuleType
            )
                .apply {
                    getNavigatableDependency = createNavigatableDependencyCallback(project, projectBuildFile)
                }

            modules += projectModule
            externalProject.addChildrenToListRecursive(modules, projectModule, project)
        }
    }
}
