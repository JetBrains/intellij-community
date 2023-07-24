package com.intellij.settingsSync.notification

import com.intellij.notification.Notification
import com.intellij.openapi.components.service
import com.intellij.settingsSync.RestartReason

internal interface NotificationService {
  companion object {
    fun getInstance(): NotificationService = service<NotificationService>()
  }

  fun notifyZipSizeExceed()
  fun buildZipSizeExceedNotification(): Notification
  fun notifyRestartNeeded(reasons: Collection<RestartReason>)
  fun buildRestartNeededNotification(reasons: Collection<RestartReason>): Notification
}