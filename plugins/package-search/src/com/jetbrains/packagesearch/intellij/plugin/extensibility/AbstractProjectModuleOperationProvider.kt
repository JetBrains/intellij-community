package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.OperationType
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn

abstract class AbstractProjectModuleOperationProvider : ProjectModuleOperationProvider {

    override fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        val dependency = UnifiedDependency(
            operationMetadata.groupId,
            operationMetadata.artifactId,
            operationMetadata.newVersion,
            operationMetadata.newScope
        )

        try {
            runWriteAction {
                DependencyModifierService.getInstance(module.nativeModule.project).declaredDependencies(operationMetadata.module.nativeModule)
                    .firstOrNull {
                        it.coordinates.groupId == dependency.coordinates.groupId &&
                            it.coordinates.artifactId == dependency.coordinates.artifactId
                    }
                    ?.also {
                        DependencyModifierService.getInstance(module.nativeModule.project).updateDependency(
                            operationMetadata.module.nativeModule, it.unifiedDependency,
                            dependency
                        )
                    }
                    ?: DependencyModifierService.getInstance(module.nativeModule.project)
                        .addDependency(operationMetadata.module.nativeModule, dependency)
            }
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, dependency, e))
        }
    }

    override fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        val dependency = UnifiedDependency(
            operationMetadata.groupId,
            operationMetadata.artifactId,
            operationMetadata.currentVersion,
            operationMetadata.currentScope
        )

        try {
            runWriteAction {
                DependencyModifierService.getInstance(module.nativeModule.project).removeDependency(operationMetadata.module.nativeModule, dependency)
            }
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.REMOVE, dependency, e))
        }
    }

    override fun updateDependencyInModule(
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

        return try {
            runWriteAction {
                DependencyModifierService.getInstance(module.nativeModule.project)
                    .updateDependency(operationMetadata.module.nativeModule, oldDependency, newDependency)
            }
            emptyList()
        } catch (e: Exception) {
            listOf(OperationFailure(OperationType.REMOVE, oldDependency, e))
        }
    }

    override fun listDependenciesInModule(module: ProjectModule): Collection<UnifiedDependency> = runReadAction {
        DependencyModifierService.getInstance(module.nativeModule.project)
            .declaredDependencies(module.nativeModule)
            .map { dep -> dep.unifiedDependency }
    }

    override fun addRepositoryToModule(repository: UnifiedDependencyRepository, module: ProjectModule): List<OperationFailure<out OperationItem>> {
        try {
            runWriteAction {
                DependencyModifierService.getInstance(module.nativeModule.project).addRepository(module.nativeModule, repository)
            }
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, repository, e))
        }
    }

    override fun removeRepositoryFromModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        try {
            runWriteAction {
                DependencyModifierService.getInstance(module.nativeModule.project).deleteRepository(module.nativeModule, repository)
            }
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, repository, e))
        }
    }

    override fun listRepositoriesInModule(module: ProjectModule): Collection<UnifiedDependencyRepository> =
        runCatching {
            DependencyModifierService.getInstance(module.nativeModule.project)
                .declaredRepositories(module.nativeModule)
        }.getOrElse {
            if (it !is ProcessCanceledException) logWarn(this::class.qualifiedName!!, it)
            else throw it
            emptyList()
        }
}
