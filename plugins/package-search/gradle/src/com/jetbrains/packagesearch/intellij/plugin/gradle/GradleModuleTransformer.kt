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

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.dependencyDeclarationCallback
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal class GradleModuleTransformer : ModuleTransformer {

    companion object {

        private fun findDependencyElementIndex(dependency: DeclaredDependency): DependencyDeclarationIndexes? {
            val artifactId = dependency.coordinates.artifactId ?: return null
            val groupId = dependency.coordinates.groupId ?: return null
            val scope = dependency.unifiedDependency.scope ?: return null
            val psiElement = dependency.psiElement ?: return null
            val isKotlinDependencyInKts = dependency.psiElement?.language?.let { it::class }
                ?.qualifiedName == "org.jetbrains.kotlin.idea.KotlinLanguage"
                && groupId == "org.jetbrains.kotlin" && artifactId.startsWith("kotlin-")

            val textToSearchFor = buildString {
                appendEscapedToRegexp(scope)
                appendEscapedToRegexp("(")
                if (isKotlinDependencyInKts) {
                    append("((?:kotlin\\(\"|[\"']org.jetbrains.kotlin:kotlin-)")
                    appendEscapedToRegexp(artifactId.removePrefix("kotlin-"))
                } else {
                    append("\"(")
                    appendEscapedToRegexp("$groupId:$artifactId")
                }
                appendEscapedToRegexp(":")
                append("?(\\$?\\{?")
                appendEscapedToRegexp("${dependency.coordinates.version}")
                append("\\}?")
                append(")?[\"']\\))\\)?")
            }

            var currentPsi = psiElement
            var attempts = 0
            val compiledRegex = Regex(textToSearchFor)

            while (attempts < 5) { // why 5? usually it's 3 parents up, maybe 2, sometimes 4. 5 is a safe bet.
                val groups = compiledRegex.find(currentPsi.text)?.groups
                if (groups != null) {
                    return groups[0]?.range?.first?.let {
                        DependencyDeclarationIndexes(
                            wholeDeclarationStartIndex = currentPsi.textOffset,
                            coordinatesStartIndex = groups[1]?.range?.first?.let { currentPsi.textOffset + it }
                                ?: error("Cannot find coordinatesStartIndex for dependency $dependency in ${currentPsi.containingFile.virtualFile.path}"),
                            versionStartIndex = groups[2]?.range?.first?.let { currentPsi.textOffset + it }
                        )
                    }
                }
                currentPsi = kotlin.runCatching { currentPsi.parent }.getOrNull() ?: break
                attempts++
            }
            return null
        }
    }

    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<PackageSearchModule> {
        val nativeModulesByExternalProjectId = mutableMapOf<String, Module>()

        val rootProjects = nativeModules
            .filter { it.isNotGradleSourceSetModule() }
            .onEach { module ->
                val externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module)
                if (externalProjectId != null) nativeModulesByExternalProjectId[externalProjectId] = module
            }
            .mapNotNull { findRootExternalProjectOrNull(project, it) }
            .distinctBy { it.buildDir }

        val projectModulesByPackageSearchDir = mutableMapOf<File, PackageSearchModule>()
        rootProjects.forEach { it.buildProjectModulesRecursively(projectModulesByPackageSearchDir, nativeModulesByExternalProjectId, project) }

        return projectModulesByPackageSearchDir.values.toList()
    }

    private suspend fun ExternalProject.buildProjectModulesRecursively(
        projectModulesByPackageSearchDir: MutableMap<File, PackageSearchModule>,
        nativeModulesByExternalProjectId: Map<String, Module>,
        project: Project,
        parent: PackageSearchModule? = null
    ) {
        val nativeModule = checkNotNull(nativeModulesByExternalProjectId[id]) { "Couldn't find native module for '$id'" }
        val buildVirtualFile = buildFile?.absolutePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }

        val buildSystemType = when {
            buildVirtualFile == null -> BuildSystemType.GRADLE_CONTAINER
            isKotlinDsl(project, buildVirtualFile) -> BuildSystemType.GRADLE_KOTLIN
            else -> BuildSystemType.GRADLE_GROOVY
        }
        val scopes: List<String> = GradleExtensionsSettings.getInstance(project)
            .getExtensionsFor(nativeModule)?.configurations?.keys?.toList() ?: emptyList()

        val packageSearchModule = PackageSearchModule(
            name = name,
            nativeModule = nativeModule,
            parent = parent,
            buildFile = buildVirtualFile,
            projectDir = projectDir,
            buildSystemType = buildSystemType,
            moduleType = GradleProjectModuleType,
            availableScopes = scopes,
            dependencyDeclarationCallback = project.dependencyDeclarationCallback { findDependencyElementIndex(it) }
        )

        for (childExternalProject in childProjects.values) {
            childExternalProject.buildProjectModulesRecursively(
                projectModulesByPackageSearchDir,
                nativeModulesByExternalProjectId,
                project,
                parent = packageSearchModule
            )
        }

        projectModulesByPackageSearchDir[projectDir] = packageSearchModule
    }

    private suspend fun isKotlinDsl(
        project: Project,
        buildVirtualFile: VirtualFile
    ) = readAction { runCatching { PsiManager.getInstance(project).findFile(buildVirtualFile) } }
        .getOrNull()
        ?.language
        ?.displayName
        ?.contains("kotlin", ignoreCase = true) == true

    private fun Module.isNotGradleSourceSetModule(): Boolean {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) return false
        return ExternalSystemApiUtil.getExternalModuleType(this) != GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }

    private fun findRootExternalProjectOrNull(project: Project, module: Module): ExternalProject? {
        val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        if (rootProjectPath == null) {
            logDebug(this::class.qualifiedName) {
                "Root external project was not yet imported, project=${project.projectFilePath}, module=${module.moduleFilePath}"
            }
            return null
        }

        val externalProjectDataCache = ExternalProjectDataCache.getInstance(project)
        return externalProjectDataCache.getRootExternalProject(rootProjectPath)
    }
}
