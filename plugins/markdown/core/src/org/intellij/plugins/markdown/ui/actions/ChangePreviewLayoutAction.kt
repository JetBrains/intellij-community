package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware

internal abstract class ChangePreviewLayoutAction(
  private val layout: TextEditorWithPreview.Layout
): ToggleAction(layout.getName(), layout.getName(), layout.getIcon(null)), DumbAware {
  override fun isSelected(event: AnActionEvent): Boolean {
    val editor = MarkdownActionUtil.findSplitEditor(event)
    return editor?.layout == layout
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val editor = MarkdownActionUtil.findSplitEditor(event) ?: return
    if (state) {
      editor.layout = layout
    } else if (layout == TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW) {
      editor.isVerticalSplit = !editor.isVerticalSplit
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val editor = MarkdownActionUtil.findSplitEditor(event) ?: return
    event.presentation.icon = layout.getIcon(editor)
  }

  class EditorOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR)

  class EditorAndPreview: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)

  class PreviewOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_PREVIEW)
}
