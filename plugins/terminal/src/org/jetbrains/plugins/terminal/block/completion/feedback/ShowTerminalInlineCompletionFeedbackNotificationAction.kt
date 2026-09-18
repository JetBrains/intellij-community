// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.feedback

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils

internal class ShowTerminalInlineCompletionFeedbackNotificationAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    TerminalFeedbackUtils.showFeedbackNotificationOnDemand(project, TerminalInlineCompletionFeedbackSurvey::class)
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
