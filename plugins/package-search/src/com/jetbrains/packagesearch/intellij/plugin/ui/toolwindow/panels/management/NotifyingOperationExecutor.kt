package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.OperationFailureRenderer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFailure
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logError
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls

internal class NotifyingOperationExecutor(
    private val project: Project,
    coroutineScope: CoroutineScope = project.lifecycleScope
) : OperationExecutor {

    private val innerExecutor = PackageManagementOperationExecutor(
        coroutineScope = coroutineScope,
        onOperationsSuccessful = project.packageSearchProjectService::notifyOperationExecuted,
        onOperationsFail = { failureType, failures ->
            onOperationsFail(project, failureType, failures)
        }
    )

    private val operationFailureRenderer = OperationFailureRenderer()

    override fun executeOperations(operations: List<PackageSearchOperation<*>>) =
        innerExecutor.executeOperations(operations)

    override fun executeOperations(operations: Deferred<List<PackageSearchOperation<*>>>) =
        innerExecutor.executeOperations(operations)

    private fun onOperationsFail(
        project: Project,
        failureType: PackageManagementOperationExecutor.FailureType,
        failures: List<PackageSearchOperationFailure>
    ) {
        val subtitle = when (failureType) {
            PackageManagementOperationExecutor.FailureType.SOME -> PackageSearchBundle.message(
                "packagesearch.operation.error.subtitle.someFailed"
            )
            PackageManagementOperationExecutor.FailureType.ALL -> PackageSearchBundle.message(
                "packagesearch.operation.error.subtitle.allFailed"
            )
        }

        val message = operationFailureRenderer.renderFailuresAsHtmlBulletList(failures)

        showErrorNotification(project, subtitle, message)
    }

    private fun showErrorNotification(
        project: Project,
        @Nls subtitle: String? = null,
        @Nls message: String
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
}
