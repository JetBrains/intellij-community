// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.statistics

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.util.PlatformUtils

internal class PyCommunityUpdateReadyNotificationListener : Notifications {
  override fun notify(notification: Notification) {
    if (!PlatformUtils.isPyCharmCommunity()) return
    val targetGroupId = UpdateChecker.getNotificationGroupForIdeUpdateResults().displayId
    val restartDisplayId = "ide.update.suggest.restart"
    if (notification.groupId == targetGroupId && notification.displayId == restartDisplayId) {
      PyCommunityUnifiedPromoFusCollector.UpdateReadyRestartNotificationShown.log()
    }
  }
}
