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

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.OperationType
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.writeAction

abstract class AbstractCoroutineProjectModuleOperationProvider : CoroutineProjectModuleOperationProvider {

    companion object {

        suspend fun addDependencyToModule(
            operationMetadata: DependencyOperationMetadata,
            module: ProjectModule
        ): List<OperationFailure<out OperationItem>> {
            val dependency = UnifiedDependency(
                operationMetadata.groupId,
                operationMetadata.artifactId,
                operationMetadata.newVersion,
                operationMetadata.newScope
            )

            module.buildFile ?: return listOf(
                OperationFailure(OperationType.ADD, dependency, InvalidVirtualFileAccessException("Build file does not exist"))
            )

            val failures = mutableListOf<OperationFailure<out OperationItem>>()
            val modifierService = DependencyModifierService.getInstance(module.nativeModule.project)

            catchingReadAction { modifierService.declaredDependencies(operationMetadata.module.nativeModule) }
                .map { it.firstOrNull { it.unifiedDependency == dependency } }
                .getOrNull()
                ?.runCatching {
                    writeAction { modifierService.updateDependency(operationMetadata.module.nativeModule, unifiedDependency, dependency) }
                }
                ?.getOrNull()
                ?: catchingWriteAction { modifierService.addDependency(operationMetadata.module.nativeModule, dependency) }
                    .onFailure { failures.add(OperationFailure(OperationType.ADD, dependency, it)) }

            return failures
        }

        suspend fun removeDependencyFromModule(
            operationMetadata: DependencyOperationMetadata,
            module: ProjectModule
        ): List<OperationFailure<out OperationItem>> {
            val dependency = UnifiedDependency(
                operationMetadata.groupId,
                operationMetadata.artifactId,
                operationMetadata.currentVersion,
                operationMetadata.currentScope
            )

            module.buildFile ?: return listOf(
                OperationFailure(OperationType.REMOVE, dependency, InvalidVirtualFileAccessException("Build file does not exist"))
            )

            val failures = mutableListOf<OperationFailure<out OperationItem>>()
            val modifierService = DependencyModifierService.getInstance(module.nativeModule.project)

            catchingWriteAction { modifierService.removeDependency(operationMetadata.module.nativeModule, dependency) }
                .onFailure { failures.add(OperationFailure(OperationType.REMOVE, dependency, it)) }

            return failures
        }

        suspend fun updateDependencyInModule(
            operationMetadata: DependencyOperationMetadata,
            module: ProjectModule
        ): List<OperationFailure<out OperationItem>> {
            val oldDependency = UnifiedDependency(
                operationMetadata.groupId,
                operationMetadata.artifactId,
                operationMetadata.currentVersion,
                operationMetadata.currentScope
            )
            val newDependency = UnifiedDependency(
                operationMetadata.groupId,
                operationMetadata.artifactId,
                operationMetadata.newVersion,
                operationMetadata.newScope ?: operationMetadata.currentScope
            )

            module.buildFile ?: return listOf(
                OperationFailure(OperationType.REMOVE, oldDependency, InvalidVirtualFileAccessException("Build file does not exist"))
            )

            val failures = mutableListOf<OperationFailure<out OperationItem>>()
            val modifierService = DependencyModifierService.getInstance(module.nativeModule.project)

            catchingWriteAction { modifierService.updateDependency(operationMetadata.module.nativeModule, oldDependency, newDependency) }
                .onFailure { failures.add(OperationFailure(OperationType.REMOVE, oldDependency, it)) }

            return failures
        }

        suspend fun declaredDependenciesInModule(module: ProjectModule) =
            module.buildFile
                ?.let { catchingReadAction { DependencyModifierService.getInstance(module.nativeModule.project).declaredDependencies(module.nativeModule) } }
                ?.onFailure {
                    logWarn(this::class.qualifiedName!! + "#declaredDependenciesInModule", it) {
                        "Error while listing declared dependencies in module ${module.name}"
                    }
                }
                ?.getOrElse { emptyList() }
                ?: emptyList()

        suspend fun addRepositoryToModule(
            repository: UnifiedDependencyRepository,
            module: ProjectModule
        ): List<OperationFailure<out OperationItem>> {
            val failures = mutableListOf<OperationFailure<out OperationItem>>()
            val modifierService = DependencyModifierService.getInstance(module.nativeModule.project)

            module.buildFile ?: return listOf(
                OperationFailure(OperationType.ADD, repository, InvalidVirtualFileAccessException("Build file does not exist"))
            )

            catchingWriteAction { modifierService.addRepository(module.nativeModule, repository) }
                .onFailure { failures.add(OperationFailure(OperationType.ADD, repository, it)) }

            return failures
        }

        suspend fun removeRepositoryFromModule(
            repository: UnifiedDependencyRepository,
            module: ProjectModule
        ): List<OperationFailure<out OperationItem>> {
            val failures = mutableListOf<OperationFailure<out OperationItem>>()
            val modifierService = DependencyModifierService.getInstance(module.nativeModule.project)

            module.buildFile ?: return listOf(
                OperationFailure(OperationType.ADD, repository, InvalidVirtualFileAccessException("Build file does not exist"))
            )

            catchingWriteAction { modifierService.deleteRepository(module.nativeModule, repository) }
                .onFailure { failures.add(OperationFailure(OperationType.ADD, repository, it)) }

            return failures
        }

        suspend fun listRepositoriesInModule(module: ProjectModule) =
            module.buildFile?.let {
                catchingReadAction {
                    DependencyModifierService.getInstance(module.nativeModule.project)
                        .declaredRepositories(module.nativeModule)
                }.onFailure {
                    logWarn(this::class.qualifiedName!! + "#listRepositoriesInModule", it) {
                        "Error while listing repositories in module ${module.name}"
                    }
                }.getOrElse { emptyList() }
            } ?: emptyList()
    }

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = false

    override fun hasSupportFor(projectModuleType: ProjectModuleType) = false

    override suspend fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> = Companion.addDependencyToModule(operationMetadata, module)

    override suspend fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> = Companion.removeDependencyFromModule(operationMetadata, module)

    override suspend fun updateDependencyInModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> = Companion.updateDependencyInModule(operationMetadata, module)

    override suspend fun declaredDependenciesInModule(module: ProjectModule) = Companion.declaredDependenciesInModule(module)

    override suspend fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>): List<UnifiedDependency> = emptyList()

    override suspend fun addRepositoryToModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> = Companion.addRepositoryToModule(repository, module)

    override suspend fun removeRepositoryFromModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> = Companion.removeRepositoryFromModule(repository, module)

    override suspend fun listRepositoriesInModule(module: ProjectModule): List<UnifiedDependencyRepository> =
        Companion.listRepositoriesInModule(module)
}

private inline fun <reified T : Throwable, R> Result<R>.except(): Result<R> = onFailure { if (it is T) throw it }

private suspend fun <T> catchingReadAction(action: () -> T) = readAction { runCatching { action() } }

private suspend fun <T> catchingWriteAction(action: () -> T) = writeAction { runCatching { action() } }
