// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenSettings
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture

class UseBundledMavenQuickFix: BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHome = MavenServerManager.BUNDLED_MAVEN_3
    Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", "Maven version has been changed to ${MavenServerManager.getInstance().currentMavenVersion}", NotificationType.INFORMATION).notify(null)
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "use_bundled_maven_quick_fix"
  }

}