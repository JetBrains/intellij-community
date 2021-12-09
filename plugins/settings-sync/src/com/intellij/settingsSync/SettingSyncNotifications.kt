package com.intellij.settingsSync

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsContexts

private const val NOTIFICATION_GROUP = "settingsSync.errors"

internal fun notifyError(@NlsContexts.NotificationTitle title: String, @NlsContexts.NotificationContent   message: String) {
  NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
    .createNotification(title, message, NotificationType.ERROR)
    .notify(null)
}
