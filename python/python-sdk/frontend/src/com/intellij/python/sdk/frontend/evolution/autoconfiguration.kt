package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.python.sdk.frontend.PySdkFrontendBundle

internal val autoSetupWithAIAction: AnAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts.ai") },
  { "" },
  AllIcons.Toolwindows.ToolWindowAskAI,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

internal val defaultUvAction: AnAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts.uv") },
  { "" },
  AllIcons.Language.Python,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
  }
}
