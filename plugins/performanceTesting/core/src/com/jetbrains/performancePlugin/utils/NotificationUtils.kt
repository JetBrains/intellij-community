// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.notification.impl.NotificationSettings
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.diagnostic.Logger
import kotlin.text.toRegex

/**
 * @See com.intellij.driver.sdk.NotificationUtils
 */
object NotificationUtils {
  private val LOG: Logger = Logger.getInstance(NotificationUtils::class.java)

  @JvmStatic
  fun disableAllBalloonNotifications() {
    NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS = false
  }

  private fun disableBalloonNotifications(condition: (NotificationSettings) -> Boolean) {
    val conf = NotificationsConfiguration.getNotificationsConfiguration()
    if (conf is NotificationsConfigurationImpl) {
      val allSettings = conf.allSettings
      for (setting in allSettings) {
        if (condition(setting)) {
          LOG.info("Disable notification group ${setting.groupId}")
          conf.changeSettings(setting.copy(displayType = NotificationDisplayType.NONE))
        }
      }
    }
  }

  @JvmStatic
  fun disableBalloonNotificationsByGroupIdPattern(groupIdsPattern: String) {
    disableBalloonNotifications { it.groupId.matches(groupIdsPattern.toRegex()) }
  }

  @JvmStatic
  fun disableAllBalloonNotificationsWithExcludesPattern(excludesPattern: String) {
    disableBalloonNotifications { !it.groupId.matches(excludesPattern.toRegex()) }
  }
}