// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle

fun catchNotificationText(
    project: Project,
    parentDisposable: Disposable,
    waitForNotificationLonger: Boolean = false,
    action: () -> Unit
): String? {
    val notifications = catchNotifications(project, parentDisposable, waitForNotificationLonger, action).ifEmpty { return null }
    return notifications.single().content
}

fun catchNotificationText(
    project: Project,
    groupId: String,
    parentDisposable: Disposable,
    waitForNotificationLonger: Boolean = false,
    action: () -> Unit
): String? {
    val notifications = catchNotifications(project, groupId, parentDisposable, waitForNotificationLonger, action).ifEmpty { return null }
    return notifications.single().content
}

suspend fun catchNotificationTextAsync(
    project: Project,
    groupId: String,
    parentDisposable: Disposable,
    action: suspend () -> Unit
): String? {
    val notifications = catchNotificationsAsync(project, groupId, parentDisposable, action).ifEmpty { return null }
    return notifications.single().content
}

fun catchNotifications(
    project: Project,
    groupId: String,
    parentDisposable: Disposable,
    waitForNotificationLonger: Boolean = false,
    action: () -> Unit
) =
    catchNotifications(project, parentDisposable, waitForNotificationLonger, action).filter { it.groupId == groupId }

suspend fun catchNotificationsAsync(project: Project, groupId: String, parentDisposable: Disposable, action: suspend () -> Unit) =
    catchNotificationsAsync(project, parentDisposable, action).filter { it.groupId == groupId }

fun catchNotifications(
    project: Project,
    parentDisposable: Disposable,
    waitForNotificationLonger: Boolean = false,
    action: () -> Unit
): List<Notification> {
    val myDisposable = Disposer.newDisposable(parentDisposable)
    try {
        val notifications = mutableListOf<Notification>()
        val connection = project.messageBus.connect(myDisposable)
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })

        action()
        connection.deliverImmediately()
        /*
        Some notifications are emitted with `NonBlockingReadAction` in smart mode. The problem is that the `NonBlockingReadAction`
        with `.inSmartMode(project)` requires indexing to finish. It might not be that fast in tests.
        Also, a notification might not have been triggered yet because the settings change listener fires asynchronously.
        To overcome these problems, we need this retry loop, and this approach is widely used in the project in tests.
         */
        if (waitForNotificationLonger) {
            for (i in 1..5) {
                if (notifications.isNotEmpty()) break
                ApplicationManager.getApplication().invokeAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
                Thread.sleep(5)
            }
        } else {
            ApplicationManager.getApplication().invokeAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
        }
        return notifications
    } finally {
        Disposer.dispose(myDisposable)
    }
}

suspend fun catchNotificationsAsync(project: Project, parentDisposable: Disposable, action: suspend () -> Unit): List<Notification> {
    val myDisposable = Disposer.newDisposable(parentDisposable)
    try {
        val notifications = mutableListOf<Notification>()
        val connection = project.messageBus.connect(myDisposable)
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })

        action()
        connection.deliverImmediately()
        ApplicationManager.getApplication().invokeAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
        return notifications
    } finally {
        Disposer.dispose(myDisposable)
    }
}

val Notification.asText: String get() = "Title: '$title'\nContent: '$content'"
fun List<Notification>.asText(filterNotificationAboutNewKotlinVersion: Boolean = true): String =
    sortedBy { it.content }
        .filter {
            !filterNotificationAboutNewKotlinVersion ||
                    !it.content.contains(
                        KotlinMigrationBundle.message(
                            "kotlin.external.compiler.updates.notification.content.0",
                            KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion
                        )
                    )
        }
        .joinToString(separator = "\n-----\n", transform = Notification::asText)