package com.intellij.settingsSync.notification

import com.intellij.ide.util.propComponentProperty
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

internal class NotificationServiceImpl: NotificationService {
  override fun notifySateRestoreFailed() {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("sync.notification.git.state.restore.failed.title"),
                          SettingsSyncBundle.message("sync.notification.git.state.restore.failed.text"),
                          NotificationType.ERROR)
    notification.notify(null)
  }

  override fun notifyZipSizeExceed() {
    val notification = buildZipSizeExceedNotification() ?: return
    notification.notify(null)
  }

  private fun buildZipSizeExceedNotification(): Notification? {
    var showNotification: Boolean by propComponentProperty(null, "sync.notification.zip.size.exceed.show", defaultValue = true)
    if (!showNotification) return null
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("sync.notification.size.exceed.title"),
                          SettingsSyncBundle.message("sync.notification.size.exceed.text"),
                          NotificationType.ERROR)
    notification.addAction(
      NotificationAction.createSimpleExpiring(SettingsSyncBundle.message("sync.notification.do.not.ask.again")) {
        showNotification = false
      }
    )
    return notification
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

  private fun buildRestartNeededNotification(reasons: Collection<RestartReason>): Notification {
    fun getMultiReasonRestartMessage(): String {
      val message = StringBuilder(SettingsSyncBundle.message("sync.notification.restart.message.list.title")).append("<br/>")

      val sortedRestartReasons = reasons.sorted()
      for ((counter, reason) in sortedRestartReasons.withIndex()) {
        message.append(reason.getMultiReasonNotificationListEntry(counter + 1))
        if (counter < sortedRestartReasons.lastIndex) message.append("<br/>")
      }

      return message.toString()
    }

    val message = when {
      reasons.isEmpty() -> ""
      reasons.size == 1 -> reasons.first().getSingleReasonNotificationMessage()
      else -> getMultiReasonRestartMessage()
    }
    return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("sync.restart.notification.title"),
                          message,
                          NotificationType.INFORMATION)
  }
}