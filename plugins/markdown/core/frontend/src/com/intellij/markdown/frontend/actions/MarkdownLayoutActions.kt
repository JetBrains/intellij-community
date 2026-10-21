// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.RegistryManager
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview

/** Shows the Markdown text editor alone with live preview off. */
internal class MarkdownEditorOnlyLayoutAction :
  ToggleAction(Layout.SHOW_EDITOR.getName(), Layout.SHOW_EDITOR.getName(), Layout.SHOW_EDITOR.getIcon(null)),
  DumbAware,
  ActionRemoteBehaviorSpecification.Frontend {

  override fun isSelected(event: AnActionEvent): Boolean {
    val editor = MarkdownActionUtil.findSplitEditor(event) ?: return false
    return editor.getLayout() == Layout.SHOW_EDITOR && !editor.isLivePreviewLayout
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      MarkdownActionUtil.findSplitEditor(event)?.setLayout(Layout.SHOW_EDITOR)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (e.place == ActionPlaces.ACTION_SEARCH) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}

/** Shows the Markdown text editor alone with live preview on. The `markdown.live.preview.enabled` registry key shows this action. */
internal class MarkdownLivePreviewLayoutAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = RegistryManager.getInstance().`is`(MarkdownEditorWithPreview.LIVE_PREVIEW_REGISTRY_KEY)
  }

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
