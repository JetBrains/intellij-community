package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank

internal class ModuleOperationExecutor {

    /** This **MUST** run on EDT */
    fun doOperation(operation: PackageSearchOperation<*>) = try {
        when (operation) {
            is PackageSearchOperation.Package.Install -> installPackage(operation)
            is PackageSearchOperation.Package.Remove -> removePackage(operation)
            is PackageSearchOperation.Package.ChangeInstalled -> changePackage(operation)
            is PackageSearchOperation.Repository.Install -> installRepository(operation)
            is PackageSearchOperation.Repository.Remove -> removeRepository(operation)
        }
        null
    } catch (e: OperationException) {
        logWarn("ModuleOperationExecutor#doOperation()", e) { "Failure while performing operation $operation" }
        PackageSearchOperationFailure(operation, e)
    }

    private fun installPackage(operation: PackageSearchOperation.Package.Install) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#installPackage()") { "Installing package ${operation.model.displayName} in ${projectModule.name}" }

        operationProvider.addDependencyToModule(
            operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model, operation.newVersion, operation.newScope),
            module = projectModule
        ).throwIfAnyFailures()

        PackageSearchEventsLogger.logPackageInstalled(operation.model, operation.projectModule)
        logTrace("ModuleOperationExecutor#installPackage()") { "Package ${operation.model.displayName} installed in ${projectModule.name}" }
    }

    private fun removePackage(operation: PackageSearchOperation.Package.Remove) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#removePackage()") { "Removing package ${operation.model.displayName} from ${projectModule.name}" }

        operationProvider.removeDependencyFromModule(
            operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model),
            module = projectModule
        ).throwIfAnyFailures()

        PackageSearchEventsLogger.logPackageRemoved(operation.model, operation.projectModule)
        logTrace("ModuleOperationExecutor#removePackage()") { "Package ${operation.model.displayName} removed from ${projectModule.name}" }
    }

    private fun changePackage(operation: PackageSearchOperation.Package.ChangeInstalled) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#changePackage()") { "Changing package ${operation.model.displayName} in ${projectModule.name}" }

        operationProvider.updateDependencyInModule(
            operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model, operation.newVersion, operation.newScope),
            module = projectModule
        ).throwIfAnyFailures()

        PackageSearchEventsLogger.logPackageUpdated(operation.model, operation.projectModule)
        logTrace("ModuleOperationExecutor#changePackage()") { "Package ${operation.model.displayName} changed in ${projectModule.name}" }
    }

    private fun dependencyOperationMetadataFrom(
        projectModule: ProjectModule,
        dependency: UnifiedDependency,
        newVersion: PackageVersion? = null,
        newScope: PackageScope? = null
    ) = DependencyOperationMetadata(
        module = projectModule,
        groupId = dependency.coordinates.groupId.nullIfBlank() ?: throw OperationException.InvalidPackage(dependency),
        artifactId = dependency.coordinates.artifactId.nullIfBlank() ?: throw OperationException.InvalidPackage(dependency),
        currentVersion = dependency.coordinates.version.nullIfBlank(),
        currentScope = dependency.scope.nullIfBlank(),
        newVersion = newVersion?.versionName.nullIfBlank() ?: dependency.coordinates.version.nullIfBlank(),
        newScope = newScope?.scopeName.nullIfBlank() ?: dependency.scope.nullIfBlank()
    )

    private fun installRepository(operation: PackageSearchOperation.Repository.Install) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#installRepository()") { "Installing repository ${operation.model.displayName} in ${projectModule.name}" }

        operationProvider.addRepositoryToModule(operation.model, projectModule)
            .throwIfAnyFailures()

        PackageSearchEventsLogger.logRepositoryAdded(operation.model)
        logTrace("ModuleOperationExecutor#installRepository()") { "Repository ${operation.model.displayName} installed in ${projectModule.name}" }
    }

    private fun removeRepository(operation: PackageSearchOperation.Repository.Remove) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#removeRepository()") { "Removing repository ${operation.model.displayName} from ${projectModule.name}" }

        operationProvider.removeRepositoryFromModule(operation.model, projectModule)
            .throwIfAnyFailures()

        PackageSearchEventsLogger.logRepositoryRemoved(operation.model)
        logTrace("ModuleOperationExecutor#removeRepository()") { "Repository ${operation.model.displayName} removed from ${projectModule.name}" }
    }

    private fun List<OperationFailure<*>>.throwIfAnyFailures() {
        when {
            isEmpty() -> return
            size > 1 -> throw IllegalStateException("A single operation resulted in multiple failures")
        }
    }
}
