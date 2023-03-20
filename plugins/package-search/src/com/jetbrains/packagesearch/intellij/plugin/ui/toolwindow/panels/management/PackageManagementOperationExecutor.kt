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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.withBackgroundLoadingBar
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

internal class PackageManagementOperationExecutor(private val project: Project) {

    private val operations: MutableMap<PackageSearchModule, MutableList<() -> Result>> = mutableMapOf()

    internal sealed class Result {

        object Success : Result()
        data class Failure(val message: String) : Result() {
            companion object {

                fun from(projectName: String, verb: String, modelDisplayName: String) =
                    Failure("$projectName - $verb $modelDisplayName")
            }
        }
    }

    fun installPackage(
        model: UnifiedDependency,
        packageSearchModule: PackageSearchModule,
    ) {
        val operationProvider = DependencyModifierService.getInstance(packageSearchModule.nativeModule.project)

        operations.put(packageSearchModule) {
            logDebug("ModuleOperationExecutor#installPackage()") { "Installing package ${model.displayName} in ${packageSearchModule.name}" }
            val result = runCatching { operationProvider.addDependency(packageSearchModule.nativeModule, model) }

            if (result.isSuccess) {
                PackageSearchEventsLogger.logPackageInstalled(
                    packageIdentifier = model.coordinates.toIdentifier(),
                    packageVersion = PackageVersion.from(model.coordinates.version),
                    targetModule = packageSearchModule
                )
            }

            logDebug("ModuleOperationExecutor#installPackage()", throwable = result.exceptionOrNull()) {
                if (result.isFailure) {
                    "Package ${model.displayName} installed in ${packageSearchModule.name}"
                } else {
                    "Package ${model.displayName} failed to be installed"
                }
            }
            if (result.isSuccess) Result.Success else Result.Failure.from(
                packageSearchModule.name,
                PackageSearchBundle.message("packagesearch.operation.verb.install"),
                model.displayName
            )
        }
    }

    private fun UnifiedCoordinates.toIdentifier() = PackageIdentifier("$groupId:$artifactId")

    fun removePackage(
        model: UnifiedDependency,
        packageSearchModule: PackageSearchModule,
    ) {
        val operationProvider = DependencyModifierService.getInstance(packageSearchModule.nativeModule.project)

        operations.put(packageSearchModule) {
            logDebug("ModuleOperationExecutor#removePackage()") { "Removing package ${model.displayName} from ${packageSearchModule.name}" }
            val result = runCatching { operationProvider.removeDependency(packageSearchModule.nativeModule, model) }

            if (result.isSuccess) {
                PackageSearchEventsLogger.logPackageRemoved(
                    packageIdentifier = model.coordinates.toIdentifier(),
                    packageVersion = PackageVersion.from(model.coordinates.version),
                    targetModule = packageSearchModule
                )
            }

            logDebug("ModuleOperationExecutor#removePackage()", throwable = result.exceptionOrNull()) {
                if (result.isSuccess) {
                    "Package ${model.displayName} removed from ${packageSearchModule.name}"
                } else {
                    "Package ${model.displayName} failed to be removed"
                }
            }

            if (result.isSuccess) Result.Success else Result.Failure.from(
                packageSearchModule.name,
                PackageSearchBundle.message("packagesearch.operation.verb.remove"),
                model.displayName
            )
        }
    }

    fun changePackage(
        model: UnifiedDependency,
        packageSearchModule: PackageSearchModule,
        newVersion: String? = null,
        newScope: String? = null
    ) {
        val operationProvider = DependencyModifierService.getInstance(packageSearchModule.nativeModule.project)

        operations.put(packageSearchModule) {
            if (newScope == null && newVersion == null) return@put Result.Success
            logDebug("ModuleOperationExecutor#changePackage()") { "Changing package ${model.displayName} in ${packageSearchModule.name}" }

            val result = runCatching {
                operationProvider.updateDependency(
                    module = packageSearchModule.nativeModule,
                    oldDescriptor = model,
                    newDescriptor = model.copy(
                        coordinates = model.coordinates.copy(
                            version = newVersion ?: model.coordinates.version
                        ),
                        scope = newScope ?: model.scope
                    )
                )
            }

            if (result.isSuccess) {
                PackageSearchEventsLogger.logPackageUpdated(
                    packageIdentifier = model.coordinates.toIdentifier(),
                    packageFromVersion = PackageVersion.from(model.coordinates.version),
                    packageVersion = PackageVersion.from(newVersion),
                    targetModule = packageSearchModule
                )
            }

            logDebug("ModuleOperationExecutor#changePackage()", throwable = result.exceptionOrNull()) {
                if (result.isSuccess) {
                    "Package ${model.displayName} changed in ${packageSearchModule.name}"
                } else {
                    "Package ${model.displayName} failed to be changed"
                }
            }

            if (result.isSuccess) Result.Success else Result.Failure.from(
                packageSearchModule.name,
                PackageSearchBundle.message("packagesearch.operation.verb.change"),
                model.displayName
            )
        }
    }

    fun installRepository(
        model: UnifiedDependencyRepository,
        packageSearchModule: PackageSearchModule
    ) {
        val operationProvider = DependencyModifierService.getInstance(packageSearchModule.nativeModule.project)

        operations.put(packageSearchModule) {
            logDebug("ModuleOperationExecutor#installRepository()") { "Installing repository ${model.displayName} in ${packageSearchModule.name}" }

            val result = runCatching { operationProvider.addRepository(packageSearchModule.nativeModule, model) }

            if (result.isSuccess) PackageSearchEventsLogger.logRepositoryAdded(model)

            logDebug("ModuleOperationExecutor#installRepository()", throwable = result.exceptionOrNull()) {
                if (result.isSuccess) {
                    "Repository ${model.displayName} installed in ${packageSearchModule.name}"
                } else {
                    "Repository ${model.displayName} failed to be installed"
                }
            }

            if (result.isSuccess) Result.Success else Result.Failure.from(
                packageSearchModule.name,
                PackageSearchBundle.message("packagesearch.operation.verb.install"),
                model.displayName
            )
        }
    }

    fun removeRepository(
        model: UnifiedDependencyRepository,
        packageSearchModule: PackageSearchModule
    ) {
        val operationProvider = DependencyModifierService.getInstance(packageSearchModule.nativeModule.project)

        operations.put(packageSearchModule) {
            logDebug("ModuleOperationExecutor#removeRepository()") { "Removing repository ${model.displayName} from ${packageSearchModule.name}" }

            val result = runCatching { operationProvider.deleteRepository(packageSearchModule.nativeModule, model) }

            if (result.isSuccess) {
                PackageSearchEventsLogger.logRepositoryRemoved(model)
            }

            logDebug("ModuleOperationExecutor#removeRepository()", throwable = result.exceptionOrNull()) {
                if (result.isSuccess) {
                    "Repository ${model.displayName} removed from ${packageSearchModule.name}"
                } else {
                    "Repository ${model.displayName} failed to be removed"
                }
            }

            if (result.isSuccess) Result.Success else Result.Failure.from(
                packageSearchModule.name,
                PackageSearchBundle.message("packagesearch.operation.verb.remove"),
                model.displayName
            )
        }
    }

    suspend fun execute() {
        val suspender = coroutineSuspender()
        withBackgroundLoadingBar(
            project = project,
            title = PackageSearchBundle.message("toolwindow.stripe.Dependencies"),
            isIndeterminate = false,
            cancellable = true,
            isPausable = true
        ) {
            withContext(suspender) {
                val suspenderJob = attachSuspender(this, suspender)
                addOnComputationInterruptedCallback { cancel() }
                val results = mutableMapOf<PackageSearchModule, List<Result>>()
                try {
                    operations.entries.forEachIndexed { index, (module, groupedOperations) ->
                        val moduleResultList = mutableListOf<Result>().also { results[module] = it }
                        progressChannel.send(index.toDouble() / operations.size)
                        messageChannel.send(PackageSearchBundle.message("packagesearch.ui.modifyingDependencies", module.name))

                        groupedOperations.forEach { operation ->
                            writeAction { moduleResultList.add(operation()) }
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        suspenderJob.cancel()
                        val failures = results.values
                            .asSequence()
                            .flatten()
                            .filterIsInstance<Result.Failure>()
                            .map { it.message }
                            .toList()
                        if (failures.isNotEmpty()) onOperationsFail(failures)
                    }
                }
            }
        }
    }

    private fun onOperationsFail(failures: List<String>) {
        showErrorNotification(
            subtitle = PackageSearchBundle.message(
                "packagesearch.operation.error.subtitle.someFailed"
            ),
            message = buildString {
                append("<html><head></head><body><ul>")
                for (failure in failures) {
                    append("<li>${failure}</li>")
                }
                append("</ul></body></html>")
            }
        )
    }

    private fun showErrorNotification(
        @Nls subtitle: String? = null,
        message: String
    ) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(
            PluginEnvironment.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID
        )
        if (notificationGroup == null) {
            logError { "Notification group ${PluginEnvironment.PACKAGE_SEARCH_NOTIFICATION_GROUP_ID} is not properly registered" }
        }

        @Suppress("DialogTitleCapitalization") // It's the Package Search plugin name
        notificationGroup
            .createNotification(
                title = PackageSearchBundle.message("packagesearch.title"),
                content = message,
                type = NotificationType.ERROR
            )
            .setSubtitle(subtitle)
            .notify(project)
    }

    private fun MutableMap<PackageSearchModule, MutableList<() -> Result>>.put(
        packageSearchModule: PackageSearchModule,
        operation: () -> Result
    ) =
        getOrPut(packageSearchModule) { mutableListOf() }.add(operation)
}

internal fun PackageManagementOperationExecutor.changePackage(
    groupId: String,
    artifactId: String,
    version: PackageVersion,
    scope: PackageScope,
    packageSearchModule: PackageSearchModule,
    newVersion: PackageVersion? = null,
    newScope: PackageScope? = null
) = changePackage(
    UnifiedDependency(
        groupId = groupId,
        artifactId = artifactId,
        version = version.takeIf { it !is PackageVersion.Missing }?.displayName,
        configuration = scope.takeIf { it !is PackageScope.Missing }?.displayName
    ),
    packageSearchModule,
    newVersion = newVersion.takeIf { it !is PackageVersion.Missing }?.displayName,
    newScope = newScope.takeIf { it !is PackageScope.Missing }?.displayName
)

internal fun PackageManagementOperationExecutor.installPackage(
    groupId: String,
    artifactId: String,
    version: PackageVersion,
    scope: PackageScope,
    packageSearchModule: PackageSearchModule,
) = installPackage(
    model = UnifiedDependency(
        groupId = groupId,
        artifactId = artifactId,
        version = version.takeIf { it !is PackageVersion.Missing }?.displayName,
        configuration = scope.takeIf { it !is PackageScope.Missing }?.displayName
    ),
    packageSearchModule = packageSearchModule,
)

internal fun PackageManagementOperationExecutor.removePackage(
    groupId: String,
    artifactId: String,
    version: PackageVersion,
    scope: PackageScope,
    packageSearchModule: PackageSearchModule,
) = removePackage(
    model = UnifiedDependency(
        groupId = groupId,
        artifactId = artifactId,
        version = version.takeIf { it !is PackageVersion.Missing }?.displayName,
        configuration = scope.takeIf { it !is PackageScope.Missing }?.displayName
    ),
    packageSearchModule = packageSearchModule,
)