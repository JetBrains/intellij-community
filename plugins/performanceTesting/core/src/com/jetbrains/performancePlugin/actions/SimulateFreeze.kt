package com.jetbrains.performancePlugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

internal class SimulateFreeze : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val durationString = Messages.showInputDialog(
      e.project,
      "Enter freeze duration in ms",
      "Freeze Simulator",
      null,
      "",
      object : InputValidator {
        override fun checkInput(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
        override fun canClose(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
      }) ?: return
    simulatedFreeze(durationString.toLong())
  }

  // Keep it a function to detect it in EA
  private fun simulatedFreeze(ms: Long) {
    Thread.sleep(ms)
  }
}
