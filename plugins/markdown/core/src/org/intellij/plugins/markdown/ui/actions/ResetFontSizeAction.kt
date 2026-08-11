// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles

internal class ResetFontSizeAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val preview = MarkdownActionUtil.findPreviewBrowserActions(event) ?: return
    preview.changeFontSize(PreviewLAFThemeStyles.defaultFontSize, temporary = true)
  }

  override fun update(event: AnActionEvent) {
    val preview = if (event.place == ActionPlaces.TOOLBAR) {
      event.presentation.text = IdeBundle.message("action.reset.font.size", PreviewLAFThemeStyles.defaultFontSize)
      MarkdownActionUtil.findPreviewBrowserActions(event)
    } else {
      MarkdownActionUtil.findPreviewBrowserActions(event)
    }
    val toReset = PreviewLAFThemeStyles.defaultFontSize
    val currentSize = preview?.getTemporaryFontSize() ?: toReset
    event.presentation.isEnabledAndVisible = preview != null && currentSize != toReset
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
