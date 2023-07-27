// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.RunnerBundle
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture

class UseBundledMavenQuickFix: BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHome = MavenServerManager.BUNDLED_MAVEN_3
    Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "",
                 RunnerBundle.message("maven.change.version.notification", MavenServerManager.getInstance().currentMavenVersion),
                 NotificationType.INFORMATION).notify(null)
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "use_bundled_maven_quick_fix"
  }

}