package com.intellij.settingsSync.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.settingsSync.NOTIFICATION_GROUP
import com.intellij.settingsSync.RestartReason
import com.intellij.settingsSync.SettingsSyncBundle
import java.lang.RuntimeException

internal class NotificationServiceImpl: NotificationService {
  override fun notifyZipSizeExceed() {
    val notification = buildZipSizeExceedNotification()
    notification.notify(null)
  }

  override fun buildZipSizeExceedNotification(): Notification {
    return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("sync.notification.size.exceed.title"),
                          SettingsSyncBundle.message("sync.notification.size.exceed.text"),
                          NotificationType.ERROR)
  }

  override fun notifyRestartNeeded(reasons: Collection<RestartReason>) {
    val notification = buildRestartNeededNotification(reasons)
    notification.addAction(NotificationAction.create(
      SettingsSyncBundle.message("sync.restart.notification.action", ApplicationNamesInfo.getInstance().fullProductName),
      com.intellij.util.Consumer {
        val app = ApplicationManager.getApplication() as ApplicationEx
        app.restart(true)
      }))
    notification.notify(null)
  }

  override fun buildRestartNeededNotification(reasons: Collection<RestartReason>): Notification {
    fun getMultiReasonRestartMessage(): String {
      assert(reasons.size > 1)
      val message = StringBuilder(SettingsSyncBundle.message("sync.restart.notification.message.subtitle")).append('\n')

      val sortedRestartReasons = reasons.sorted()
      for ((counter, reason) in sortedRestartReasons.withIndex()) {
        message.append(reason.getMultiReasonNotificationListEntry(counter + 1))
      }

      message.dropLast(0) // we do not need the new line in the end
      return message.toString()
    }

    val message = when {
      reasons.isEmpty() -> throw RuntimeException("No restart reasons provided")
      reasons.size == 1 -> reasons.first().getSingleReasonNotificationMessage()
      else -> getMultiReasonRestartMessage()
    }
    return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("sync.restart.notification.title"),
                          message,
                          NotificationType.INFORMATION)
  }
}