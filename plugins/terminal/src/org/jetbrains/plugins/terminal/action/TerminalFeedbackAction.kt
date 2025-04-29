// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.feedback.impl.state.CommonFeedbackSurveyService
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.feedback.ReworkedTerminalSurveyConfig

@ApiStatus.Internal
class TerminalFeedbackAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible =
      project != null &&
      TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED &&
      ReworkedTerminalSurveyConfig.checkIdeIsSuitable()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = ReworkedTerminalSurveyConfig.createFeedbackDialog(project, forTest = false)
    if (dialog.showAndGet()) {
      CommonFeedbackSurveyService.feedbackSurveyAnswerSent(ReworkedTerminalSurveyConfig.surveyId)
    }
  }
}
