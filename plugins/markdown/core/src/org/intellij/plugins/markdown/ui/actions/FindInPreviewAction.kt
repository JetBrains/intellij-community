// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class FindInPreviewAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    MarkdownActionUtil.findPreviewBrowserActions(event)?.showSearchBar()
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
