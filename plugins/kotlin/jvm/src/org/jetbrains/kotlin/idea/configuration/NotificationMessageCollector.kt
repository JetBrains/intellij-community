// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.idea.KotlinJvmBundle

open class NotificationMessageCollector(
    private val project: Project,
    private val groupDisplayId: String,
    @NlsContexts.NotificationTitle private val title: String
) {
    private val messages = ArrayList<String>()

    fun addMessage(message: String): NotificationMessageCollector {
        messages.add(message)
        return this
    }

    fun showNotification() {
        if (messages.isEmpty()) return
        Notifications.Bus.notify(Notification(groupDisplayId, title, resultMessage, NotificationType.INFORMATION), project)
    }

    private val resultMessage: String
        get() {
            val singleMessage = messages.singleOrNull()
            if (singleMessage != null) return singleMessage

            return messages.joinToString(separator = "<br/><br/>")
        }
}

fun createConfigureKotlinNotificationCollector(project: Project): NotificationMessageCollector = NotificationMessageCollector(
    project,
    "Configure Kotlin: info notification",
    KotlinJvmBundle.message("configure.kotlin")
)