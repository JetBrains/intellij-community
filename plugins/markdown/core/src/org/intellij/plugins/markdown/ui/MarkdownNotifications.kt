// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

internal object MarkdownNotifications {
  val group
    get() = requireNotNull(NotificationGroupManager.getInstance().getNotificationGroup("Markdown"))

  fun showError(project: Project?, @NlsSafe title: String = "", @NlsSafe message: String) {
    group.createNotification(title, message, NotificationType.ERROR).notify(project)
  }

  fun showWarning(project: Project?, @NlsSafe title: String = "", @NlsSafe message: String) {
    group.createNotification(title, message, NotificationType.WARNING).notify(project)
  }

  fun showInfo(project: Project?, @NlsSafe title: String = "", @NlsSafe message: String) {
    group.createNotification(title, message, NotificationType.INFORMATION).notify(project)
  }
}
