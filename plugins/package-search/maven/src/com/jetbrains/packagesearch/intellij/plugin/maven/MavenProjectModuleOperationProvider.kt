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

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

private val MAVEN_CENTRAL_UNIFIED_REPOSITORY = UnifiedDependencyRepository(
    "central",
    "Central Repository",
    "https://repo.maven.apache.org/maven2",
)

internal class MavenProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {

    companion object {

        fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
            projectModuleType is MavenProjectModuleType

        fun hasSupportFor(project: Project, psiFile: PsiFile?) = MavenUtil.isPomFile(project, psiFile?.virtualFile)
    }

    override fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ) = super.addDependencyToModule(
        operationMetadata = operationMetadata.copy(
            newScope = operationMetadata.newScope?.takeIf { it != PackageSearchMavenConfiguration.DEFAULT_MAVEN_SCOPE }
        ),
        module = module
    )

    override fun usesSharedPackageUpdateInspection() = true

    override fun hasSupportFor(projectModuleType: ProjectModuleType) = Companion.hasSupportFor(projectModuleType)

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = Companion.hasSupportFor(project, psiFile)

    // This is a (sad) workaround for IDEA-267229 — when that's sorted, we shouldn't need this anymore.
    override fun listRepositoriesInModule(module: ProjectModule): Collection<UnifiedDependencyRepository> {
        val repositories = super.listRepositoriesInModule(module)
        return if (repositories.none { it.id == MAVEN_CENTRAL_UNIFIED_REPOSITORY.id })
            repositories + MAVEN_CENTRAL_UNIFIED_REPOSITORY
        else
            repositories
    }

    override suspend fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>) = readAction {
        MavenProjectsManager.getInstance(module.nativeModule.project)
            .findProject(module.nativeModule)
            ?.dependencies
            ?.map { UnifiedDependency(it.groupId, it.artifactId, it.version, it.scope) }
            ?: emptyList()
    }
}
