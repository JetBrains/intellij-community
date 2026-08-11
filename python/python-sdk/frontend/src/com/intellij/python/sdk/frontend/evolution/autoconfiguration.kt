package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons

internal val autoSetupWithAIAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts.ai") },
  { "" },
  AllIcons.Toolwindows.ToolWindowAskAI,
) {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

internal val defaultUvAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts.uv") },
  { "" },
  PythonSdkFrontendIcons.Logo,
) {
  override fun actionPerformed(e: AnActionEvent) {
  }
}
