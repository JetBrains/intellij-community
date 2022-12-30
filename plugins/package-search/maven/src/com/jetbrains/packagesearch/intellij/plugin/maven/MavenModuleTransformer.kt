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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.asSafely
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.dependencyDeclarationCallback
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
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
        return ProjectModule(
            name = mavenProject.name ?: nativeModule.name,
            nativeModule = nativeModule,
            parent = null,
            buildFile = buildFile,
            projectDir = mavenProject.directoryFile.toNioPath().toFile(),
            buildSystemType = BuildSystemType.MAVEN,
            moduleType = MavenProjectModuleType,
            availableScopes = PackageSearchMavenConfiguration.getInstance(project).getMavenScopes(),
            dependencyDeclarationCallback = project.dependencyDeclarationCallback { dependency ->
                val children: Array<PsiElement> = dependency.psiElement.asSafely<XmlTag>()
                    ?.children
                    ?: return@dependencyDeclarationCallback null
                val xmlTag = children.filterIsInstance<XmlText>()
                    .find { it is Navigatable && it.canNavigate() }
                    ?: return@dependencyDeclarationCallback null
                DependencyDeclarationIndexes(
                    wholeDeclarationStartIndex = xmlTag.textOffset,
                    coordinatesStartIndex = xmlTag.textOffset,
                    versionStartIndex = children.filterIsInstance<XmlTag>()
                        .find { it.name == "version" }
                        ?.children
                        ?.filterIsInstance<XmlText>()
                        ?.firstOrNull()
                        ?.textOffset
                )
            }
        )
    }
}

val BuildSystemType.Companion.MAVEN
    get() = BuildSystemType(
        name = "MAVEN", language = "xml", dependencyAnalyzerKey = MavenUtil.SYSTEM_ID, statisticsKey = "maven"
    )
