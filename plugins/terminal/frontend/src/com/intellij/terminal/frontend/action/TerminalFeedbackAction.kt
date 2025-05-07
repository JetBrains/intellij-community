package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.feedback.isSuitableToShowByExplicitUserAction
import com.intellij.platform.feedback.showFeedbackDialog
import org.jetbrains.plugins.terminal.block.feedback.ReworkedTerminalSurveyConfig

internal class TerminalFeedbackAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && ReworkedTerminalSurveyConfig.isSuitableToShowByExplicitUserAction(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ReworkedTerminalSurveyConfig.showFeedbackDialog(project, forTest = false)
  }
}