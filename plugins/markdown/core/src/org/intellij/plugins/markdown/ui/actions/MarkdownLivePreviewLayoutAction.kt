// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import org.intellij.plugins.markdown.MarkdownBundle

internal class MarkdownLivePreviewLayoutAction : ToggleAction(
  MarkdownBundle.message("action.Markdown.Layout.LivePreview.text"),
  null,
  AllIcons.General.LayoutPreviewOnly,
), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(event: AnActionEvent): Boolean {
    return MarkdownActionUtil.findSplitEditor(event)?.isLivePreviewLayout == true
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      MarkdownActionUtil.findSplitEditor(event)?.setLivePreviewLayout()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
