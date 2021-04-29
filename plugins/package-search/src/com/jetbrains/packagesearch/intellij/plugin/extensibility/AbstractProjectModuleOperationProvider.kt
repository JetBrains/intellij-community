package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.OperationType
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class AbstractProjectModuleOperationProvider : ProjectModuleOperationProvider {

    override fun addDependencyToProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        val dependency = UnifiedDependency(
            operationMetadata.groupId,
            operationMetadata.artifactId,
            operationMetadata.newVersion,
            operationMetadata.newScope
        )

        try {
            DependencyModifierService.getInstance(project).declaredDependencies(operationMetadata.module.nativeModule)
                .firstOrNull { it.coordinates.groupId == dependency.coordinates.groupId && it.coordinates.artifactId == dependency.coordinates.artifactId }
                ?.also {
                    DependencyModifierService.getInstance(project).updateDependency(
                        operationMetadata.module.nativeModule, it.unifiedDependency,
                        dependency
                    )
                } ?: DependencyModifierService.getInstance(project).addDependency(operationMetadata.module.nativeModule, dependency)
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, dependency, e))
        }
    }

    override fun removeDependencyFromProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        val dependency = UnifiedDependency(
            operationMetadata.groupId,
            operationMetadata.artifactId,
            operationMetadata.currentVersion,
            operationMetadata.currentScope
        )

        try {
            DependencyModifierService.getInstance(project).removeDependency(operationMetadata.module.nativeModule, dependency)
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.REMOVE, dependency, e))
        }
    }

    override fun updateDependencyInProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
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

        try {
            DependencyModifierService.getInstance(project)
                .updateDependency(operationMetadata.module.nativeModule, oldDependency, newDependency)
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.REMOVE, oldDependency, e))
        }
    }

    override fun listDependenciesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependency> {
        val module = runReadAction { ModuleUtilCore.findModuleForFile(virtualFile, project) }
        return module?.let {
            DependencyModifierService.getInstance(project).declaredDependencies(it).map { dep ->
                dep.unifiedDependency
            }
        } ?: emptyList()
    }

    override fun addRepositoryToProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        val module = runReadAction { ModuleUtilCore.findModuleForFile(virtualFile, project) }
            ?: return listOf(OperationFailure(OperationType.ADD, repository, IllegalArgumentException()))

        try {
            DependencyModifierService.getInstance(project).addRepository(module, repository)
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, repository, e))
        }
    }

    override fun removeRepositoryFromProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        val module = runReadAction { ModuleUtilCore.findModuleForFile(virtualFile, project) }
            ?: return listOf(OperationFailure(OperationType.ADD, repository, IllegalArgumentException()))

        try {
            DependencyModifierService.getInstance(project).deleteRepository(module, repository)
            return emptyList()
        } catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, repository, e))
        }
    }

    override fun listRepositoriesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependencyRepository> {
        val module = runReadAction { ModuleUtilCore.findModuleForFile(virtualFile, project) }
        return module?.let { DependencyModifierService.getInstance(project).declaredRepositories(it) } ?: emptyList()
    }
}
