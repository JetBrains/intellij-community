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

@file:Suppress("ThrowableNotThrown")

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractCoroutineProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File
import kotlin.random.Random

private const val FILE_TYPE_GROOVY = "groovy"
private const val FILE_TYPE_KOTLIN = "kotlin"
private const val EXTENSION_GRADLE = "gradle"
private const val EXTENSION_GRADLE_KTS = "gradle.kts"

internal open class GradleProjectModuleOperationProvider : AbstractCoroutineProjectModuleOperationProvider() {

    companion object {

        fun hasSupportFor(psiFile: PsiFile?): Boolean {
            // Logic based on com.android.tools.idea.gradle.project.sync.GradleFiles.isGradleFile()
            val file = psiFile?.virtualFile ?: return false

            val isGroovyFile = FILE_TYPE_GROOVY.equals(psiFile.fileType.name, ignoreCase = true)
            val isKotlinFile = FILE_TYPE_KOTLIN.equals(psiFile.fileType.name, ignoreCase = true)

            if (!isGroovyFile && !isKotlinFile) return false
            return file.name.endsWith(EXTENSION_GRADLE, ignoreCase = true) || file.name.endsWith(EXTENSION_GRADLE_KTS, ignoreCase = true)
        }

        fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
            projectModuleType is GradleProjectModuleType

        private fun getDependencyTaskScript(taskName: String, outputFile: File, scopes: Set<String>, gradlePath: String) =
            // language=Groovy
            """
                allprojects {
                    if (project.path == '${gradlePath}' || ':' + rootProject.projectDir.name + project.path == '$gradlePath}') {
                        tasks.register('${taskName}') {
                            def outputFile = project.file("${FileUtil.toCanonicalPath(outputFile.absolutePath)}")
                            outputs.file(outputFile)
                            doLast {
                                def json = []
                                def scopes = [${scopes.joinToString { "'$it'" }}]
                                for (configuration in configurations) {
                                    if (configuration.name in scopes) {
                                        def deps = []
                                        for (dependency in configuration.allDependencies) {
                                            if (dependency.group != null && dependency.version != null) {
                                                deps.add([groupId: dependency.group, artifactId: dependency.name, version: dependency.version])
                                            }
                                        }
                                        json.add([configurationName: configuration.name, dependencies: deps])
                                    }
                                }
                                outputFile.write(groovy.json.JsonOutput.toJson(json))
                            }
                        }
                    }
                }
            """.trimIndent()
    }

    private val gradleMutex = Mutex()

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = Companion.hasSupportFor(psiFile)

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean = Companion.hasSupportFor(projectModuleType)

    override suspend fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        saveAdditionalScopeToConfigurationIfNeeded(module.nativeModule.project, requireNotNull(operationMetadata.newScope) {
            PackageSearchBundle.getMessage("packagesearch.operation.error.gradle.missing.configuration")
        })

        return super.addDependencyToModule(operationMetadata, module)
    }

    override suspend fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.currentScope) {
            PackageSearchBundle.getMessage("packagesearch.operation.error.gradle.missing.configuration")
        }
        return super.removeDependencyFromModule(operationMetadata, module)
    }

    private fun saveAdditionalScopeToConfigurationIfNeeded(project: Project, scopeName: String) {
        val configuration = PackageSearchGradleConfiguration.getInstance(project)
        if (configuration.updateScopesOnUsage) {
            configuration.addGradleScope(scopeName)
        }
    }

    override suspend fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>) =
        module.nativeModule.project
            .service<GradleConfigurationReportNodeProcessor.Cache>()
            .state[module.projectDir.absolutePath]
            ?.configurations
            ?.asSequence()
            ?.filter { it.name in scopes }
            ?.flatMap { configuration ->
                configuration.dependencies.map { UnifiedDependency(it.groupId, it.artifactId, it.version, configuration.name) }
            }
            ?.toList()
            ?: emptyList()
}
