package com.intellij.mermaid

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls

internal object MermaidNotifications {
  val group
    get() = requireNotNull(NotificationGroupManager.getInstance().getNotificationGroup("Mermaid"))

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
