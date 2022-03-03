package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ModuleOperationExecutor {

    /** This **MUST** run on EDT */
    suspend fun doOperation(operation: PackageSearchOperation<*>) = try {
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

    private suspend fun installPackage(operation: PackageSearchOperation.Package.Install) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#installPackage()") { "Installing package ${operation.model.displayName} in ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule,
            dependency = operation.model,
            newVersion = operation.newVersion,
            newScope = operation.newScope
        )

        withEDT { operationProvider.addDependencyToModule(operationMetadata, projectModule).throwIfAnyFailures() }

        PackageSearchEventsLogger.logPackageInstalled(
            packageIdentifier = operation.model.coordinates.toIdentifier(),
            packageVersion = operation.newVersion,
            targetModule = operation.projectModule
        )
        logTrace("ModuleOperationExecutor#installPackage()") { "Package ${operation.model.displayName} installed in ${projectModule.name}" }
    }

    private fun UnifiedCoordinates.toIdentifier() = PackageIdentifier("$groupId:$artifactId")

    private suspend fun removePackage(operation: PackageSearchOperation.Package.Remove) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#removePackage()") { "Removing package ${operation.model.displayName} from ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model)

        withEDT { operationProvider.removeDependencyFromModule(operationMetadata, projectModule).throwIfAnyFailures() }

        PackageSearchEventsLogger.logPackageRemoved(
            packageIdentifier = operation.model.coordinates.toIdentifier(),
            packageVersion = operation.currentVersion,
            targetModule = operation.projectModule
        )
        logTrace("ModuleOperationExecutor#removePackage()") { "Package ${operation.model.displayName} removed from ${projectModule.name}" }
    }

    private suspend fun changePackage(operation: PackageSearchOperation.Package.ChangeInstalled) {
        val projectModule = operation.projectModule
        val operationProvider = readAction {
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
                ?: throw OperationException.unsupportedBuildSystem(projectModule)
        }

        logDebug("ModuleOperationExecutor#changePackage()") { "Changing package ${operation.model.displayName} in ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule,
            dependency = operation.model,
            newVersion = operation.newVersion,
            newScope = operation.newScope
        )

        withEDT { operationProvider.updateDependencyInModule(operationMetadata, projectModule).throwIfAnyFailures() }

        PackageSearchEventsLogger.logPackageUpdated(
            packageIdentifier = operation.model.coordinates.toIdentifier(),
            packageFromVersion = operation.currentVersion,
            packageVersion = operation.newVersion,
            targetModule = operation.projectModule
        )
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

    private suspend fun installRepository(operation: PackageSearchOperation.Repository.Install) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#installRepository()") { "Installing repository ${operation.model.displayName} in ${projectModule.name}" }

        withEDT { operationProvider.addRepositoryToModule(operation.model, projectModule).throwIfAnyFailures() }

        PackageSearchEventsLogger.logRepositoryAdded(operation.model)
        logTrace("ModuleOperationExecutor#installRepository()") { "Repository ${operation.model.displayName} installed in ${projectModule.name}" }
    }

    private suspend fun removeRepository(operation: PackageSearchOperation.Repository.Remove) {
        val projectModule = operation.projectModule
        val operationProvider = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        logDebug("ModuleOperationExecutor#removeRepository()") { "Removing repository ${operation.model.displayName} from ${projectModule.name}" }

        withEDT { operationProvider.removeRepositoryFromModule(operation.model, projectModule).throwIfAnyFailures() }

        PackageSearchEventsLogger.logRepositoryRemoved(operation.model)
        logTrace("ModuleOperationExecutor#removeRepository()") { "Repository ${operation.model.displayName} removed from ${projectModule.name}" }
    }

    private fun List<OperationFailure<*>>.throwIfAnyFailures() {
        when {
            isEmpty() -> return
            size > 1 -> error("A single operation resulted in multiple failures")
        }
    }

    private suspend inline fun <T> withEDT(noinline action: suspend CoroutineScope.() -> T) =
        withContext(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement(), action)
}
