// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable
import java.util.concurrent.CompletableFuture

class OpenMavenRunnerSettingsQuickFix(val search: String?) : BuildIssueQuickFix {
  constructor() : this(null)

  override val id: String = "open_maven_runner_settings_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    ApplicationManager.getApplication().invokeLater {
      ShowSettingsUtilImpl.showSettingsDialog(project, MavenRunnerConfigurable.SETTINGS_ID, search)
    }
    return CompletableFuture.completedFuture(null)
  }
}