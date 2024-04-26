// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.build.issue.quickfix.OpenFileQuickFix.Companion.showFile
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.execution.SyncBundle.message
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.server.MavenWrapperSupport.Companion.getWrapperProperties
import org.jetbrains.idea.maven.statistics.MavenNotificationDisplayIds

class MavenWrapperEventLogNotification {
  companion object {
    @JvmStatic
    fun noDistributionUrlEvent(project: Project, multiModuleDir: String) {
      val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Maven") ?: return
      ApplicationManager.getApplication().invokeLater {
        val wrapperPropertyFile = LocalFileSystem.getInstance().findFileByPath(multiModuleDir)?.let { getWrapperProperties(it) }
        if (wrapperPropertyFile == null) {
          wrapperPropertyFileNotFound(notificationGroup, project)
        }
        else {
          distributionUrlEmpty(notificationGroup, project, wrapperPropertyFile)
        }
      }
    }

    @JvmStatic
    fun informationEvent(project: Project, @Nls content: String) {
      val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Maven") ?: return
      ApplicationManager.getApplication().invokeLater {
        notificationGroup
          .createNotification(
            message("maven.wrapper.notification.title"), content, NotificationType.INFORMATION
          ).setDisplayId(MavenNotificationDisplayIds.WRAPPER_INFORMATION)
          .notify(project)
      }
    }

    @JvmStatic
    fun errorDownloading(project: Project, error: String) {
      if (ApplicationManager.getApplication().isUnitTestMode) return; // project leak in tests

      val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Maven") ?: return
      ApplicationManager.getApplication().invokeLater {
        notificationGroup
          .createNotification(
            message("maven.wrapper.notification.title"),
            message("maven.wrapper.notification.downloading.error.content", error),
            NotificationType.ERROR
          )
          .setDisplayId(MavenNotificationDisplayIds.WRAPPER_DOWNLOADING_ERROR)
          .addAction(NotificationAction.createSimple(message("maven.wrapper.notification.downloading.error.action")) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project,
              MavenProjectBundle.message("configurable.MavenSettings.display.name"))
          })
          .notify(project)
      }
    }

    private fun wrapperPropertyFileNotFound(notificationGroup: NotificationGroup,
                                            project: Project) {
      notificationGroup
        .createNotification(
          message("maven.wrapper.notification.title"),
          message("maven.wrapper.notification.empty.url.content.file.not.found"),
          NotificationType.WARNING
        )
        .setDisplayId(MavenNotificationDisplayIds.WRAPPER_FILE_NOT_FOUND)
        .addAction(NotificationAction.createSimple(message("maven.wrapper.notification.empty.url.action.disable")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project,
            MavenProjectBundle.message("configurable.MavenSettings.display.name"))
        })
        .notify(project)
    }

    private fun distributionUrlEmpty(notificationGroup: NotificationGroup,
                                     project: Project,
                                     wrapperPropertyFile: VirtualFile) {
      notificationGroup
        .createNotification(
          message("maven.wrapper.notification.title"),
          message("maven.wrapper.notification.empty.url.content"),
          NotificationType.WARNING
        )
        .setDisplayId(MavenNotificationDisplayIds.WRAPPER_EMPTY_URL)
        .addAction(NotificationAction.createSimple(message("maven.wrapper.notification.empty.url.action.check")) {
          showFile(project, wrapperPropertyFile.toNioPath(), null)
        })
        .addAction(NotificationAction.createSimple(message("maven.wrapper.notification.empty.url.action.disable")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project,
            MavenProjectBundle.message("configurable.MavenSettings.display.name"))
        })
        .notify(project)
    }
  }
}