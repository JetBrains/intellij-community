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
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleModuleTransformer : ModuleTransformer {

    companion object {

        fun findDependencyElement(file: PsiFile, groupId: String, artifactId: String): PsiElement? {
            val isKotlinDependencyInKts = file.language::class.qualifiedName == "org.jetbrains.kotlin.idea.KotlinLanguage"
                && groupId == "org.jetbrains.kotlin" && artifactId.startsWith("kotlin-")

            val textToSearchFor = if (isKotlinDependencyInKts) {
                "kotlin(\"${artifactId.removePrefix("kotlin-")}\")"
            } else {
                "$groupId:$artifactId"
            }
            return file.firstElementContainingExactly(textToSearchFor)
        }
    }

    override fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        nativeModules.filter { it.isNotGradleSourceSetModule() }
            .mapNotNull { nativeModule ->
                val externalProject = findExternalProjectOrNull(project, nativeModule, recursiveSearch = false)
                    ?: return@mapNotNull null
                val buildFile = externalProject.buildFile ?: return@mapNotNull null
                val buildVirtualFile = LocalFileSystem.getInstance().findFileByPath(buildFile.absolutePath) ?: return@mapNotNull null
                val buildSystemType =
                    if (isKotlinDsl(project, buildVirtualFile)) {
                        BuildSystemType.GRADLE_KOTLIN
                    } else {
                        BuildSystemType.GRADLE_GROOVY
                    }
                val scopes: List<String> = GradleExtensionsSettings.getInstance(project)
                    .getExtensionsFor(nativeModule)?.configurations?.keys?.toList() ?: emptyList()

                ProjectModule(
                    name = externalProject.name,
                    nativeModule = nativeModule,
                    parent = null,
                    buildFile = buildVirtualFile,
                    buildSystemType = buildSystemType,
                    moduleType = GradleProjectModuleType,
                    navigatableDependency = createNavigatableDependencyCallback(project, buildVirtualFile),
                    availableScopes = scopes
                )
            }
            .flatMap { getAllSubmodules(project, it) }
            .distinctBy { it.buildFile }

    private fun isKotlinDsl(
        project: Project,
        buildVirtualFile: VirtualFile
    ) =
        runCatching { PsiManager.getInstance(project).findFile(buildVirtualFile) }
            .getOrNull()
            ?.language
            ?.displayName
            ?.contains("kotlin", ignoreCase = true) == true

    private fun Module.isNotGradleSourceSetModule(): Boolean {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) return false
        return ExternalSystemApiUtil.getExternalModuleType(this) != GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }

    private fun getAllSubmodules(project: Project, rootModule: ProjectModule): List<ProjectModule> {
        val externalRootProject = findExternalProjectOrNull(project, rootModule.nativeModule)
            ?: return emptyList()

        val modules = mutableListOf(rootModule)
        externalRootProject.addChildrenToListRecursive(modules, rootModule, project)
        return modules
    }

    private fun findExternalProjectOrNull(
        project: Project,
        module: Module,
        recursiveSearch: Boolean = true
    ): ExternalProject? {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
            return null
        }

        val externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module)
        if (externalProjectId == null) {
            logDebug(this::class.qualifiedName) {
                "Module has no external project ID, project=${project.projectFilePath}, module=${module.moduleFilePath}"
            }
            return null
        }

        val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        if (rootProjectPath == null) {
            logDebug(this::class.qualifiedName) {
                "Root external project was not yet imported, project=${project.projectFilePath}, module=${module.moduleFilePath}"
            }
            return null
        }

        val externalProjectDataCache = ExternalProjectDataCache.getInstance(project)
        val externalProject = externalProjectDataCache.getRootExternalProject(rootProjectPath)
        if (externalProject == null) {
            logDebug(this::class.qualifiedName) {
                "External project is not yet cached, project=${project.projectFilePath}, module=${module.moduleFilePath}"
            }
            return null
        }
        return externalProject.findProjectWithId(externalProjectId, recursiveSearch)
    }

    private fun ExternalProject.findProjectWithId(
        externalProjectId: String,
        recursiveSearch: Boolean
    ): ExternalProject? {
        if (externalProjectId == this.id) return this

        if (!recursiveSearch) return null

        val childExternalProjects = childProjects.values
        if (childExternalProjects.isEmpty()) return null

        for (childExternalProject in childExternalProjects) {
            if (childExternalProject.id == externalProjectId) return childExternalProject
            val recursiveExternalProject = childExternalProject.findProjectWithId(externalProjectId, recursiveSearch)
            if (recursiveExternalProject != null) return recursiveExternalProject
        }
        return null
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
            val nativeModule = ModuleUtilCore.findModuleForFile(projectBuildFile, project)
                ?: continue

            val projectModule = ProjectModule(
                name = externalProject.name,
                nativeModule = nativeModule,
                parent = currentModule,
                buildFile = projectBuildFile,
                buildSystemType = BuildSystemType.GRADLE_GROOVY,
                moduleType = GradleProjectModuleType,
                navigatableDependency = createNavigatableDependencyCallback(project, projectBuildFile),
                availableScopes = emptyList()
            )

            modules += projectModule
            externalProject.addChildrenToListRecursive(modules, projectModule, project)
        }
    }

    private fun createNavigatableDependencyCallback(project: Project, file: VirtualFile) =
        { groupId: String, artifactId: String, _: PackageVersion ->
            runReadAction {
                PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
                    val dependencyElement = findDependencyElement(psiFile, groupId, artifactId) ?: return@let null
                    return@let dependencyElement as Navigatable
                }
            }
        }
}

private fun PsiFile.firstElementContainingExactly(value: String): PsiElement? {
    val index = text.indexOf(value)
    if (index < 0) return null
    if (text.length > value.length && text[index + value.length] != ':') return null
    val element = getElementAtOffsetOrNull(index)
    return element
}

private fun PsiFile.getElementAtOffsetOrNull(index: Int) =
    PsiUtil.getElementAtOffset(this, index).takeIf { it != this }
