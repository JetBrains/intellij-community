// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.actions.ReimportAction
import java.util.concurrent.CompletableFuture

class MavenFullSyncQuickFix : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val action = ReimportAction()
    val event = AnActionEvent.createEvent(action, dataContext, null, "", ActionUiKind.NONE, null)
    ActionUtil.invokeAction(action, event, null)
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "maven_full_sync_quick_fix"
  }
}