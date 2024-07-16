// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.Companion.PREVIEW_JCEF_PANEL
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles

internal class ResetFontSizeAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val preview = event.getRequiredData(PREVIEW_JCEF_PANEL).get() ?: return
    preview.changeFontSize(PreviewLAFThemeStyles.defaultFontSize, temporary = true)
  }

  override fun update(event: AnActionEvent) {
    if (event.place == ActionPlaces.POPUP) {
      event.presentation.text = IdeBundle.message("action.reset.font.size", PreviewLAFThemeStyles.defaultFontSize)
      val toReset = PreviewLAFThemeStyles.defaultFontSize
      val preview = event.dataContext.getData(PREVIEW_JCEF_PANEL)?.get() ?: return
      val currentSize = preview.getTemporaryFontSize()
      event.presentation.setEnabled(currentSize != toReset)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

