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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.readAction
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.nullIfBlank

internal class ModuleOperationExecutor {

    private val operations = mutableListOf<suspend () -> Result>()

    sealed class Result {

        companion object {

            fun from(operation: PackageSearchOperation<*>, operationFailures: List<OperationFailure<out OperationItem>>) =
                if (operationFailures.isEmpty()) Success(operation) else Failure(operation, operationFailures)
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

    fun addOperation(operation: PackageSearchOperation<*>) = when (operation) {
        is PackageSearchOperation.Package.Install -> installPackage(operation)
        is PackageSearchOperation.Package.Remove -> removePackage(operation)
        is PackageSearchOperation.Package.ChangeInstalled -> changePackage(operation)
        is PackageSearchOperation.Repository.Install -> installRepository(operation)
        is PackageSearchOperation.Repository.Remove -> removeRepository(operation)
    }

    fun addOperations(operations: Iterable<PackageSearchOperation<*>>) = operations.forEach { addOperation(it) }

    private fun installPackage(operation: PackageSearchOperation.Package.Install) {
        val projectModule = operation.projectModule
        val operationProvider =
            CoroutineProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule, dependency = operation.model, newVersion = operation.newVersion, newScope = operation.newScope
        )

        operations.add {
            logDebug("ModuleOperationExecutor#installPackage()") { "Installing package ${operation.model.displayName} in ${projectModule.name}" }
            val errors = operationProvider.addDependencyToModule(operationMetadata, projectModule)

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

            Result.from(operation, errors)
        }
    }

    private fun UnifiedCoordinates.toIdentifier() = PackageIdentifier("$groupId:$artifactId")

    private fun removePackage(operation: PackageSearchOperation.Package.Remove) {
        val projectModule = operation.projectModule
        val operationProvider =
            CoroutineProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )

        val operationMetadata = dependencyOperationMetadataFrom(projectModule, operation.model)
        operations.add {
            logDebug("ModuleOperationExecutor#removePackage()") { "Removing package ${operation.model.displayName} from ${projectModule.name}" }
            val errors = operationProvider.removeDependencyFromModule(operationMetadata, projectModule)

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

            Result.from(operation, errors)
        }
    }

    private fun changePackage(operation: PackageSearchOperation.Package.ChangeInstalled) {
        val projectModule = operation.projectModule
        val operationProvider = CoroutineProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
            ?: throw OperationException.unsupportedBuildSystem(projectModule)

        val operationMetadata = dependencyOperationMetadataFrom(
            projectModule = projectModule, dependency = operation.model, newVersion = operation.newVersion, newScope = operation.newScope
        )

        operations.add {

            logDebug("ModuleOperationExecutor#changePackage()") { "Changing package ${operation.model.displayName} in ${projectModule.name}" }

            val errors = operationProvider.updateDependencyInModule(operationMetadata, projectModule)

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

            Result.from(operation, errors)
        }
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

    private fun installRepository(operation: PackageSearchOperation.Repository.Install) {
        val projectModule = operation.projectModule
        val operationProvider =
            CoroutineProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )
        operations.add {
            logDebug("ModuleOperationExecutor#installRepository()") { "Installing repository ${operation.model.displayName} in ${projectModule.name}" }

            val errors = operationProvider.addRepositoryToModule(operation.model, projectModule)

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

            Result.from(operation, errors)
        }
    }

    private fun removeRepository(operation: PackageSearchOperation.Repository.Remove) {
        val projectModule = operation.projectModule
        val operationProvider =
            CoroutineProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType) ?: throw OperationException.unsupportedBuildSystem(
                projectModule
            )
        operations.add {
            logDebug("ModuleOperationExecutor#removeRepository()") { "Removing repository ${operation.model.displayName} from ${projectModule.name}" }

            val errors = operationProvider.removeRepositoryFromModule(operation.model, projectModule)

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

            Result.from(operation, errors)
        }
    }

    suspend fun execute() = operations.map { it() }
}
