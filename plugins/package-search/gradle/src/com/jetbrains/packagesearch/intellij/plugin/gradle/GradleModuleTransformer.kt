/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.Dependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationCallback
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleModuleTransformer : ModuleTransformer {

    companion object {

        private fun findDependencyElementIndex(file: PsiFile, dependency: Dependency): DependencyDeclarationIndexes? {
            val isKotlinDependencyInKts = file.language::class.qualifiedName == "org.jetbrains.kotlin.idea.KotlinLanguage"
                && dependency.groupId == "org.jetbrains.kotlin" && dependency.artifactId.startsWith("kotlin-")

            val textToSearchFor = buildString {
                appendEscapedToRegexp(dependency.scope)
                append("[\\(\\s]+")
                if (isKotlinDependencyInKts) {
                    append("(")
                    appendEscapedToRegexp("kotlin(\"")
                    appendEscapedToRegexp(dependency.artifactId.removePrefix("kotlin-"))
                    appendEscapedToRegexp("\")")
                } else {
                    append("[\\'\\\"]")
                    append("(")
                    appendEscapedToRegexp("${dependency.groupId}:${dependency.artifactId}:")
                    append("(\\\$?\\{?")
                    appendEscapedToRegexp(dependency.version)
                    append("\\}?)")
                }
                append(")")
                append("[\\'\\\"]")
                appendEscapedToRegexp(")")
                append("?")
            }

            val groups = Regex(textToSearchFor).find(file.text)?.groups ?: return null

            return groups[0]?.range?.first?.let {
                DependencyDeclarationIndexes(
                    wholeDeclarationStartIndex = it,
                    coordinatesStartIndex = groups[1]?.range?.first
                        ?: error("Cannot find coordinatesStartIndex for dependency $dependency in ${file.virtualFile.path}"),
                    versionStartIndex = groups[2]?.range?.first
                )
            }
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
                    availableScopes = scopes,
                    dependencyDeclarationCallback = getDependencyDeclarationCallback(project, buildVirtualFile)
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
                availableScopes = emptyList(),
                dependencyDeclarationCallback = getDependencyDeclarationCallback(project, projectBuildFile)
            )

            modules += projectModule
            externalProject.addChildrenToListRecursive(modules, projectModule, project)
        }
    }

    private fun getDependencyDeclarationCallback(
        project: Project,
        buildVirtualFile: VirtualFile
    ): DependencyDeclarationCallback = { dependency ->
        readAction {
            PsiManager.getInstance(project)
                .findFile(buildVirtualFile)
                ?.let { findDependencyElementIndex(it, dependency) }
        }
    }
}

private fun StringBuilder.appendEscapedToRegexp(text: String) =
    StringUtil.escapeToRegexp(text, this)

val BuildSystemType.Companion.GRADLE_GROOVY
    get() = BuildSystemType(name = "GRADLE", language = "groovy", dependencyAnalyzerKey = GradleConstants.SYSTEM_ID, statisticsKey = "gradle-groovy")

val BuildSystemType.Companion.GRADLE_KOTLIN
    get() = BuildSystemType(name = "GRADLE", language = "kotlin", dependencyAnalyzerKey = GradleConstants.SYSTEM_ID, statisticsKey = "gradle-kts")
