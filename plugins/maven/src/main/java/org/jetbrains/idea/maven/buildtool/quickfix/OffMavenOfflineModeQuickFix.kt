// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture

class OffMavenOfflineModeQuickFix : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val generalSettings = MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings
    ApplicationManager.getApplication().invokeLater {
      generalSettings.isWorkOffline = false
    }
    Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                 MavenProjectBundle.message("maven.offline.mode.switched.off.notification"),
                 NotificationType.INFORMATION).notify(project)
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "off_maven_offline_mode_quick_fix"
  }
}