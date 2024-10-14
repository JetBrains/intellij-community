// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.actions.ReimportAction
import java.util.concurrent.CompletableFuture

class MavenFullSyncQuickFix : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val action = ReimportAction()
    val event = createEvent(action, dataContext)
    invokeAction(action, event)
    return CompletableFuture.completedFuture(null)
  }

  private fun createEvent(action: AnAction, dataContext: DataContext): AnActionEvent {
    val p = action.templatePresentation.clone()
    return createEvent(dataContext, p)
  }

  private fun createEvent(dataContext: DataContext, presentation: Presentation): AnActionEvent {
    return AnActionEvent(
      null,
      dataContext,
      "",
      presentation,
      ActionManager.getInstance(),
      0,
    )
  }

  private fun invokeAction(action: AnAction, event: AnActionEvent) {
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      try {
        ActionUtil.performActionDumbAwareWithCallbacks(action, event)
      }
      finally {
      }
    }
  }

  companion object {
    const val ID = "maven_full_sync_quick_fix"
  }
}