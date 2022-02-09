// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

internal object MarkdownNotifier {
  val notificationGroup
    get() = requireNotNull(NotificationGroupManager.getInstance().getNotificationGroup("Markdown"))

  fun showErrorNotification(project: Project, @NlsSafe msg: String, @NlsSafe title: String = "") {
    notificationGroup.createNotification(title, msg, NotificationType.ERROR).notify(project)
  }

  fun showWarningNotification(project: Project, @NlsSafe msg: String) {
    notificationGroup.createNotification(msg, NotificationType.WARNING).notify(project)
  }

  fun showInfoNotification(project: Project, @NlsSafe msg: String) {
    notificationGroup.createNotification(msg, NotificationType.INFORMATION).notify(project)
  }

  fun notifyPandocDetected(project: Project) {
    notificationGroup.createNotification(
      MarkdownBundle.message("markdown.settings.pandoc.notification.detected"),
      NotificationType.INFORMATION
    ).notify(project)
  }

  fun notifyPandocNotDetected(project: Project) {
    notificationGroup.createNotification(
      MarkdownBundle.message("markdown.settings.pandoc.notification.not.detected"),
      NotificationType.WARNING
    ).notify(project)
  }

  fun notifyPandocDetectionFailed(project: Project, @NlsSafe msg: String) {
    notificationGroup.createNotification(msg, NotificationType.ERROR).notify(project)
  }
}
