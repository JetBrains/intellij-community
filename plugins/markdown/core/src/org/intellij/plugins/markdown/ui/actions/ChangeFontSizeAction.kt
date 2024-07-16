package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.awt.RelativePoint
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.intellij.plugins.markdown.ui.preview.jcef.zoomIndicator.PreviewZoomIndicatorManager

internal sealed class ChangeFontSizeAction(private val transform: (Int) -> Int): DumbAwareAction() {
  class Increase: ChangeFontSizeAction(transform = { it + 1 })

  class Decrease: ChangeFontSizeAction(transform = { (it - 1).coerceAtLeast(1) })

  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    checkNotNull(editor) { "Preview editor should be obtainable from the action event" }
    val preview = editor.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() ?: return
    if (preview !is MarkdownJCEFHtmlPanel) {
      return
    }
    val currentSize = preview.getTemporaryFontSize() ?: PreviewLAFThemeStyles.defaultFontSize
    val newSize = transform(currentSize)
    preview.changeFontSize(newSize, temporary = true)
    val project = event.project
    val balloon = project?.service<PreviewZoomIndicatorManager>()?.createOrGetBalloon(preview)
    balloon?.show(RelativePoint.getSouthOf(preview.component), Balloon.Position.below)
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
