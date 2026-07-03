package com.intellij.ui.webview.demo

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.project.Project

internal class ActivateWebViewDemoToolWindowAction : ActivateToolWindowAction("WebView Demo") {
  override fun hasEmptyState(project: Project): Boolean = true
}
