package com.intellij.settingsSync.notification

import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.RestartReason

interface NotificationService {
  companion object {
    fun getInstance(): NotificationService = ApplicationManager.getApplication().getService(NotificationService::class.java)
  }

  fun notifyZipSizeExceed()
  fun buildZipSizeExceedNotification(): Notification
  fun notifyRestartNeeded(reasons: Collection<RestartReason>)
  fun buildRestartNeededNotification(reasons: Collection<RestartReason>): Notification
}