package com.jetbrains.performancePlugin.utils

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.diagnostic.Logger

/**
 * @See com.intellij.driver.sdk.NotificationUtils
 */
object NotificationUtils {
  private val LOG: Logger = Logger.getInstance(NotificationUtils::class.java)

  @JvmStatic
  fun disableAllBalloonNotifications() {
    NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS = false
  }

  @JvmStatic
  fun disableBalloonNotificationsByGroupIdPattern(groupIdsPattern: String) {
    val conf = NotificationsConfiguration.getNotificationsConfiguration()
    val regex = groupIdsPattern.toRegex()
    if (conf is NotificationsConfigurationImpl) {
      val allSettings = conf.allSettings
      for (setting in allSettings) {
        if (setting.groupId.matches(regex)) {
          LOG.info("Disable notification group ${setting.groupId}")
          conf.changeSettings(setting.copy(displayType = NotificationDisplayType.NONE))
        }
      }
    }
  }
}