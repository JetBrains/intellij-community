// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.impl.RunDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import java.util.concurrent.CompletableFuture

class OpenRunConfigurationQuickFix(val myRunConfiguration: MavenRunConfiguration) : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val runManager = getInstance(project)
    val settings = myRunConfiguration.factory?.let {
      runManager.createConfiguration(myRunConfiguration, it)
    } ?: return CompletableFuture.completedFuture(null)
    runManager.selectedConfiguration = settings
    RunDialog.editConfiguration(myRunConfiguration.project, settings,
                                ExecutionBundle.message("create.run.configuration.for.item.dialog.title", settings.getName()))
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "open_run_configuration_quick_fix"
  }
}