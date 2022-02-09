package com.intellij.settingsSync

import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class DownloadSnapshotAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val remoteCommunicator = SettingsSyncMain.getInstance().getRemoteCommunicator() as CloudConfigServerCommunicator
    object: Task.Backgroundable(e.project, SettingsSyncBundle.message("progress.title.downloading.settings.from.server"), false) {
      override fun run(indicator: ProgressIndicator) {
        val zipFile = remoteCommunicator.downloadSnapshot()
        if (zipFile != null) {
          @Suppress("DialogTitleCapitalization")
          NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("", SettingsSyncBundle.message("notification.content.downloaded.latest.settings.successfully"),
                                NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring(RevealFileAction.getActionName()) {
              RevealFileAction.openFile(zipFile)
            })
            .notify(e.project)

        }
        else {
          NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(SettingsSyncBundle.message("notification.title.download.zip.file.failed"),
                                SettingsSyncBundle.message("notification.content.check.log.file.for.errors"), NotificationType.ERROR)
            .addAction(ShowLogAction.notificationAction())
            .notify(e.project)
        }
      }
    }.queue()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SettingsSyncMain.isAvailable() &&
                                         SettingsSyncMain.getInstance().getRemoteCommunicator() is CloudConfigServerCommunicator
  }
}