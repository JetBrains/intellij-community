// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

object MarkdownNotifier {
  val NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Markdown")

  fun notifyNetworkProblems(project: Project) {
    NOTIFICATION_GROUP.createNotification(
      MarkdownBundle.message("markdown.google.import.network.problems.msg"),
      NotificationType.ERROR
    ).notify(project)
  }

  fun notifyPandocDetected(project: Project) {
    NOTIFICATION_GROUP.createNotification(
      MarkdownBundle.message("markdown.settings.pandoc.notification.detected"),
      NotificationType.INFORMATION
    ).notify(project)
  }

  fun notifyPandocNotDetected(project: Project) {
    NOTIFICATION_GROUP.createNotification(
      MarkdownBundle.message("markdown.settings.pandoc.notification.not.detected"),
      NotificationType.WARNING
    ).notify(project)
  }

  fun notifyPandocDetectionFailed(project: Project, @NlsSafe msg: String) {
    NOTIFICATION_GROUP.createNotification(msg, NotificationType.ERROR).notify(project)
  }

  fun notifyIfConvertFailed(project: Project, @NlsSafe msg: String) {
    NOTIFICATION_GROUP.createNotification(msg, NotificationType.ERROR).notify(project)
  }

  fun notifyOfSuccessfulExport(project: Project, @NlsSafe msg: String) {
    NOTIFICATION_GROUP.createNotification(msg, NotificationType.INFORMATION).notify(project)
  }

  fun notifyAboutConversionWarning(project: Project, @NlsSafe msg: String) {
    NOTIFICATION_GROUP.createNotification(msg, NotificationType.WARNING).notify(project)
  }
}
