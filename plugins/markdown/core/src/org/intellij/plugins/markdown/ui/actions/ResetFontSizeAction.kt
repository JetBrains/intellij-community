// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.Companion.PREVIEW_JCEF_PANEL
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel

internal class ResetFontSizeAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    val preview = if (editor != null) {
      editor.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() as? MarkdownJCEFHtmlPanel
    } else {
      event.getRequiredData(PREVIEW_JCEF_PANEL).get()
    } ?: return

    preview.changeFontSize(PreviewLAFThemeStyles.defaultFontSize, temporary = true)
  }

  override fun update(event: AnActionEvent) {
    val preview = if (event.place == ActionPlaces.POPUP) {
      event.presentation.text = IdeBundle.message("action.reset.font.size", PreviewLAFThemeStyles.defaultFontSize)
      event.dataContext.getData(PREVIEW_JCEF_PANEL)?.get()
    } else {
      val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
      editor?.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() as? MarkdownJCEFHtmlPanel
    }
    val toReset = PreviewLAFThemeStyles.defaultFontSize
    val currentSize = preview?.getTemporaryFontSize() ?: toReset
    event.presentation.isEnabledAndVisible = preview != null && currentSize != toReset
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

