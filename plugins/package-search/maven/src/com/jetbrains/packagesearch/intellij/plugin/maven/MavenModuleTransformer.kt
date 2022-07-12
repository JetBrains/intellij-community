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

package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.future.future
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils.SearchProcessor
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

internal class MavenModuleTransformer : CoroutineModuleTransformer {

    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        nativeModules.parallelMap { nativeModule ->
            readAction { runCatching { MavenProjectsManager.getInstance(project).findProject(nativeModule) } }.onFailure {
                    logDebug(contextName = "MavenModuleTransformer", it) { "Error finding Maven module ${nativeModule.name}" }
                }.getOrNull()?.let {
                    createMavenProjectModule(project, nativeModule, it)
                }
        }.filterNotNull()

    private fun createMavenProjectModule(
        project: Project, nativeModule: Module, mavenProject: MavenProject
    ): ProjectModule {
        val buildFile = mavenProject.file
        return ProjectModule(name = mavenProject.name ?: nativeModule.name,
            nativeModule = nativeModule,
            parent = null,
            buildFile = buildFile,
            projectDir = mavenProject.directoryFile.toNioPath().toFile(),
            buildSystemType = BuildSystemType.MAVEN,
            moduleType = MavenProjectModuleType,
            availableScopes = PackageSearchMavenConfiguration.getInstance(project).getMavenScopes(),
            dependencyDeclarationCallback = { dependency ->
                project.lifecycleScope.future {
                    val artifactId = dependency.coordinates.artifactId ?: return@future null
                    val groupId = dependency.coordinates.groupId ?: return@future null
                    readAction {
                        val projectModel = MavenDomUtil.getMavenDomProjectModel(project, buildFile) ?: return@readAction null

                        val mavenDependency = KMavenNavigationUtils.findDependency(
                            mavenDomProjectModel = projectModel,
                            groupId = groupId,
                            artifactId = artifactId,
                            version = dependency.coordinates.version,
                            scope = dependency.scope
                        )
                        val dependencyIndex = when (val elem = mavenDependency?.xmlElement) {
                            is XmlTag -> elem.value.textElements.firstOrNull()?.startOffset
                            else -> null
                        }
                        dependencyIndex?.let {
                            DependencyDeclarationIndexes(
                                wholeDeclarationStartIndex = it,
                                coordinatesStartIndex = it,
                                versionStartIndex = when (val elem = mavenDependency?.version?.xmlElement) {
                                    is XmlTag -> elem.value.textElements.firstOrNull()?.startOffset
                                    else -> null
                                }
                            )
                        }
                    }
                }
            })
    }
}

val BuildSystemType.Companion.MAVEN
    get() = BuildSystemType(
        name = "MAVEN", language = "xml", dependencyAnalyzerKey = MavenUtil.SYSTEM_ID, statisticsKey = "maven"
    )

object KMavenNavigationUtils {

    fun findDependency(
        mavenDomProjectModel: MavenDomProjectModel,
        groupId: String,
        artifactId: String,
        version: String?,
        scope: String?
    ): MavenDomDependency? {
        val processor = MavenDependencyProcessor {
            it.dependencies.find { dependency ->
                val scopeAndVersionMatch = when {
                    version != null && scope != null -> dependency.version.stringValue == version && dependency.scope.stringValue == scope
                    version == null && scope != null -> dependency.scope.stringValue == scope
                    version != null && scope == null -> dependency.version.stringValue == version
                    else -> true
                }
                dependency.groupId.stringValue == groupId && dependency.artifactId.stringValue == artifactId && scopeAndVersionMatch
            }
        }
        MavenDomProjectProcessorUtils.processDependencies(mavenDomProjectModel, processor)
        return processor.result
    }

}

@Suppress("FunctionName")
private fun MavenDependencyProcessor(action: (MavenDomDependencies) -> MavenDomDependency?) =
    object : SearchProcessor<MavenDomDependency, MavenDomDependencies>() {
        override fun find(element: MavenDomDependencies) = action(element)
    }
