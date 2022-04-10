package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ModuleOperationExecutor {

    sealed class Result {

        companion object {

            fun from(operation: PackageSearchOperation<*>, operationFailures: List<OperationFailure<out OperationItem>>) =
                if (operationFailures.isEmpty()) Result.Success(operation) else Result.Failure(operation, operationFailures)
        }

        abstract val operation: PackageSearchOperation<*>

        data class Success(override val operation: PackageSearchOperation<*>) : Result()
        data class Failure(
            override val operation: PackageSearchOperation<*>, val operationFailures: List<OperationFailure<out OperationItem>>
        ) : Result() {

            val message
                get() = "${operation.projectModule.name} - " + when (operation) {
                    is PackageSearchOperation.Package.Install ->
                        "${PackageSearchBundle.message("packagesearch.operation.verb.install")} ${operation.model.displayName}"
                    is PackageSearchOperation.Package.Remove ->
                        "${PackageSearchBundle.message("packagesearch.operation.verb.remove")} ${operation.model.displayName}"
                    is PackageSearchOperation.Package.ChangeInstalled ->
                        "${PackageSearchBundle.message("packagesearch.operation.verb.change")} ${operation.model.displayName}"
                    is PackageSearchOperation.Repository.Install ->
                        "${PackageSearchBundle.message("packagesearch.operation.verb.install")} ${operation.model.displayName}"
                    is PackageSearchOperation.Repository.Remove ->
                        "${PackageSearchBundle.message("packagesearch.operation.verb.remove")} ${operation.model.displayName}"
                }
        }
    }

    /** This **MUST** run on EDT */
    suspend fun doOperation(operation: PackageSearchOperation<*>) = Result.from(
        operation = operation,
        operationFailures = when (operation) {
            is PackageSearchOperation.Package.Install -> installPackage(operation)
            is PackageSearchOperation.Package.Remove -> removePackage(operation)
            is PackageSearchOperation.Package.ChangeInstalled -> changePackage(operation)
            is PackageSearchOperation.Repository.Install -> installRepository(operation)
            is PackageSearchOperation.Repository.Remove -> removeRepository(operation)
        }
    )

    private suspend fun installPackage(operation: PackageSearchOperation.Package.Install): List<OperationFailure<out OperationItem>> {
        val projectModule = operation.projectModule
        val operationProvider =
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        logDebug("ModuleOperationExecutor#installPackage()") { "Installing package ${operation.model.displayName} in ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule, dependency = operation.model, newVersion = operation.newVersion, newScope = operation.newScope
        )

        val errors = withEDT { operationProvider.addDependencyToModule(operationMetadata, projectModule) }

        if (errors.isEmpty()) {
            PackageSearchEventsLogger.logPackageInstalled(
                packageIdentifier = operation.model.coordinates.toIdentifier(),
                packageVersion = operation.newVersion,
                targetModule = operation.projectModule
            )
        }

        logTrace("ModuleOperationExecutor#installPackage()") {
            if (errors.isEmpty()) {
                "Package ${operation.model.displayName} installed in ${projectModule.name}"
            } else {
                "Package ${operation.model.displayName} failed to be installed due to:" + "\n${errors.joinToString("\n") { it.error.stackTraceToString() }}"
            }
        }

        return errors
    }

    private fun UnifiedCoordinates.toIdentifier() = PackageIdentifier("$groupId:$artifactId")

    private suspend fun removePackage(operation: PackageSearchOperation.Package.Remove): List<OperationFailure<out OperationItem>> {
        val projectModule = operation.projectModule
        val operationProvider =
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        logDebug("ModuleOperationExecutor#removePackage()") { "Removing package ${operation.model.displayName} from ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model)

        val errors = withEDT { operationProvider.removeDependencyFromModule(operationMetadata, projectModule) }

        if (errors.isEmpty()) {
            PackageSearchEventsLogger.logPackageRemoved(
                packageIdentifier = operation.model.coordinates.toIdentifier(),
                packageVersion = operation.currentVersion,
                targetModule = operation.projectModule
            )
        }

        logTrace("ModuleOperationExecutor#removePackage()") {
            if (errors.isEmpty()) {
                "Package ${operation.model.displayName} removed from ${projectModule.name}"
            } else {
                "Package ${operation.model.displayName} failed to be removed due to:" + "\n${errors.joinToString("\n") { it.error.stackTraceToString() }}"
            }
        }

        return errors
    }

    private suspend fun changePackage(operation: PackageSearchOperation.Package.ChangeInstalled): List<OperationFailure<out OperationItem>> {
        val projectModule = operation.projectModule
        val operationProvider = readAction {
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )
        }

        logDebug("ModuleOperationExecutor#changePackage()") { "Changing package ${operation.model.displayName} in ${projectModule.name}" }

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule, dependency = operation.model, newVersion = operation.newVersion, newScope = operation.newScope
        )

        val errors = withEDT { operationProvider.updateDependencyInModule(operationMetadata, projectModule) }

        if (errors.isEmpty()) {
            PackageSearchEventsLogger.logPackageUpdated(
                packageIdentifier = operation.model.coordinates.toIdentifier(),
                packageFromVersion = operation.currentVersion,
                packageVersion = operation.newVersion,
                targetModule = operation.projectModule
            )
        }

        logTrace("ModuleOperationExecutor#changePackage()") {
            if (errors.isEmpty()) {
                "Package ${operation.model.displayName} changed in ${projectModule.name}"
            } else {
                "Package ${operation.model.displayName} failed to be changed due to:" + "\n${errors.joinToString("\n") { it.error.stackTraceToString() }}"
            }
        }

        return errors
    }

    private fun dependencyOperationMetadataFrom(
        projectModule: ProjectModule, dependency: UnifiedDependency, newVersion: PackageVersion? = null, newScope: PackageScope? = null
    ) = DependencyOperationMetadata(
        module = projectModule,
        groupId = dependency.coordinates.groupId.nullIfBlank() ?: throw OperationException.InvalidPackage(dependency),
        artifactId = dependency.coordinates.artifactId.nullIfBlank() ?: throw OperationException.InvalidPackage(dependency),
        currentVersion = dependency.coordinates.version.nullIfBlank(),
        currentScope = dependency.scope.nullIfBlank(),
        newVersion = newVersion?.versionName.nullIfBlank() ?: dependency.coordinates.version.nullIfBlank(),
        newScope = newScope?.scopeName.nullIfBlank() ?: dependency.scope.nullIfBlank()
    )

    private suspend fun installRepository(operation: PackageSearchOperation.Repository.Install): List<OperationFailure<out OperationItem>> {
        val projectModule = operation.projectModule
        val operationProvider =
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        logDebug("ModuleOperationExecutor#installRepository()") { "Installing repository ${operation.model.displayName} in ${projectModule.name}" }

        val errors = withEDT { operationProvider.addRepositoryToModule(operation.model, projectModule) }

        if (errors.isEmpty()) {
            PackageSearchEventsLogger.logRepositoryAdded(operation.model)
        }

        logTrace("ModuleOperationExecutor#installRepository()") {
            if (errors.isEmpty()) {
                "Repository ${operation.model.displayName} installed in ${projectModule.name}"
            } else {
                "Repository ${operation.model.displayName} failed to be installed due to:" + "\n${errors.joinToString("\n") { it.error.stackTraceToString() }}"
            }
        }

        return errors
    }

    private suspend fun removeRepository(operation: PackageSearchOperation.Repository.Remove): List<OperationFailure<out OperationItem>> {
        val projectModule = operation.projectModule
        val operationProvider =
            ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        logDebug("ModuleOperationExecutor#removeRepository()") { "Removing repository ${operation.model.displayName} from ${projectModule.name}" }

        val errors = withEDT { operationProvider.removeRepositoryFromModule(operation.model, projectModule) }

        if (errors.isEmpty()) {
            PackageSearchEventsLogger.logRepositoryRemoved(operation.model)
        }

        logTrace("ModuleOperationExecutor#removeRepository()") {
            if (errors.isEmpty()) {
                "Repository ${operation.model.displayName} removed from ${projectModule.name}"
            } else {
                "Repository ${operation.model.displayName} failed to be removed due to:" + "\n${errors.joinToString("\n") { it.error.stackTraceToString() }}"
            }
        }

        return errors
    }

    private suspend inline fun <T> withEDT(noinline action: suspend CoroutineScope.() -> T) =
        withContext(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement(), action)
}
