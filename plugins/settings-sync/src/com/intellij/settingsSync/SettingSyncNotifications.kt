package com.intellij.settingsSync

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsContexts

internal const val NOTIFICATION_GROUP = "settingsSync.errors"

internal fun notifySettingsSyncError(@NlsContexts.NotificationTitle title: String, @NlsContexts.NotificationContent message: String) {
  NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
    .createNotification(title, message, NotificationType.ERROR)
    .notify(null)
}
