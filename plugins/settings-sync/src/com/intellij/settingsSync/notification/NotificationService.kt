package com.intellij.settingsSync.notification

import com.intellij.openapi.components.service
import com.intellij.settingsSync.RestartReason

internal interface NotificationService {
  companion object {
    fun getInstance(): NotificationService = service<NotificationService>()
  }

  fun notifyZipSizeExceed()
  fun notifyRestartNeeded(reasons: Collection<RestartReason>)
}