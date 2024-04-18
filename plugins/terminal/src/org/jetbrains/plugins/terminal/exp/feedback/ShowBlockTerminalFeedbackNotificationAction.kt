// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.feedback

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class ShowBlockTerminalFeedbackNotificationAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    @Suppress("TestOnlyProblems")  // it is an internal action for testing
    BlockTerminalFeedbackSurvey().showNotification(e.project!!, forTest = true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}