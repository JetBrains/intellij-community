package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.util.GradleConstants

private fun PsiFile.firstElementContaining(text: String): @NotNull PsiElement? {
    val index = this.text.indexOf(text)
    return if (index >= 0) getElementAtOffsetOrNull(index) else null
}

private fun PsiFile.getElementAtOffsetOrNull(index: Int) =
    PsiUtil.getElementAtOffset(this, index).takeIf { it != this }

class GradleModuleTransformer : ModuleTransformer {

    companion object {

        fun findDependencyElement(file: PsiFile, groupId: String, artifactId: String): PsiElement? {
            val isKotlinDependency = file.language::class.qualifiedName == "org.jetbrains.kotlin.idea.KotlinLanguage"
                && groupId == "org.jetbrains.kotlin" && artifactId.startsWith("kotlin-")
            val kotlinDependencyImport = "kotlin(\"${artifactId.removePrefix("kotlin-")}\")"
            val searchableText = if (isKotlinDependency) kotlinDependencyImport else "$groupId:$artifactId"
            return file.firstElementContaining(searchableText)
        }
    }

    override fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> {
        return nativeModules.mapNotNull { nativeModule ->
            val externalRootProject = findExternalProjectOrNull(project, nativeModule) ?: return@mapNotNull null
            val buildFile = externalRootProject.buildFile ?: return@mapNotNull null
            val buildVirtualFile = LocalFileSystem.getInstance().findFileByPath(buildFile.absolutePath) ?: return@mapNotNull null
            val buildSystemType =
                if (runReadAction { PsiManager.getInstance(project).findFile(buildVirtualFile) }?.language?.displayName?.lowercase()
                        ?.contains("kotlin") == true) {
                    BuildSystemType.GRADLE_KOTLIN
                } else {
                    BuildSystemType.GRADLE_GROOVY
                }
            ProjectModule(
                name = externalRootProject.name,
                nativeModule = nativeModule,
                parent = null,
                buildFile = buildVirtualFile,
                buildSystemType = buildSystemType,
                moduleType = GradleProjectModuleType,
                navigatableDependency = createNavigatableDependencyCallback(project, buildVirtualFile)
            )
        }.flatMap { getAllSubmodules(project, it) }
            .distinctBy { it.buildFile }
    }

    private fun getAllSubmodules(project: Project, rootModule: ProjectModule): List<ProjectModule> {
        val externalRootProject = findExternalProjectOrNull(project, rootModule.nativeModule) ?: return emptyList()

        val modules = mutableListOf(rootModule)
        externalRootProject.addChildrenToListRecursive(modules, rootModule, project)
        return modules
    }

    private fun ExternalProject.addChildrenToListRecursive(
        modules: MutableList<ProjectModule>,
        currentModule: ProjectModule,
        project: Project
    ) {
        val localFileSystem = LocalFileSystem.getInstance()

        for (externalProject in childProjects.values) {
            val projectBuildFile = externalProject.buildFile?.absolutePath?.let(localFileSystem::findFileByPath)
                ?: continue
            val nativeModule = runReadAction { ModuleUtilCore.findModuleForFile(projectBuildFile, project) }
                ?: continue

            val projectModule = ProjectModule(
                name = externalProject.name,
                nativeModule = nativeModule,
                parent = currentModule,
                buildFile = projectBuildFile,
                buildSystemType = BuildSystemType.GRADLE_GROOVY,
                moduleType = GradleProjectModuleType,
                navigatableDependency = createNavigatableDependencyCallback(project, projectBuildFile)
            )

            modules += projectModule
            externalProject.addChildrenToListRecursive(modules, projectModule, project)
        }
    }

    private fun findExternalProjectOrNull(project: Project, module: Module): ExternalProject? {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
            return null
        }

        val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        if (rootProjectPath == null) {
            logDebug(this::class.qualifiedName) { "Root external project was not yet imported, project=${project.projectFilePath}, module=${module.moduleFilePath}" }
            return null
        }

        val externalProjectDataCache = ExternalProjectDataCache.getInstance(project)
        val externalProject = externalProjectDataCache.getRootExternalProject(rootProjectPath)
        if (externalProject == null) {
            logDebug(this::class.qualifiedName) { "External project is not yet cached, project=${project.projectFilePath}, module=${module.moduleFilePath}" }
            return null
        }

        return externalProject
    }

    private fun createNavigatableDependencyCallback(project: Project, file: VirtualFile) =
        { groupId: String, artifactId: String, _: PackageVersion ->
            PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
                val dependencyElement = findDependencyElement(psiFile, groupId, artifactId) ?: return@let null
                return@let dependencyElement as Navigatable
            }
        }
}
