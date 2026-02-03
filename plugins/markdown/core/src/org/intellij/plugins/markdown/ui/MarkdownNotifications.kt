// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Use these methods for creating and firing notifications instead of manually obtaining notification group.
 */
@ApiStatus.Internal
object MarkdownNotifications {
  val group
    get() = requireNotNull(NotificationGroupManager.getInstance().getNotificationGroup("Markdown"))

  fun showError(
    project: Project?,
    id: @NonNls String?,
    @NlsContexts.NotificationTitle title: String = "",
    @NlsContexts.NotificationContent message: String
  ) {
    val notification = group.createNotification(title, message, NotificationType.ERROR)
    id?.let { notification.setDisplayId(it) }
    notification.notify(project)
  }

  fun showWarning(
    project: Project?,
    id: @NonNls String?,
    @NlsContexts.NotificationTitle title: String = "",
    @NlsContexts.NotificationContent message: String
  ) {
    val notification = group.createNotification(title, message, NotificationType.WARNING)
    id?.let { notification.setDisplayId(it) }
    notification.notify(project)
  }

  fun showInfo(
    project: Project?,
    id: @NonNls String?,
    @NlsContexts.NotificationTitle title: String = "",
    @NlsContexts.NotificationContent message: String
  ) {
    val notification = group.createNotification(title, message, NotificationType.INFORMATION)
    id?.let { notification.setDisplayId(it) }
    notification.notify(project)
  }
}
