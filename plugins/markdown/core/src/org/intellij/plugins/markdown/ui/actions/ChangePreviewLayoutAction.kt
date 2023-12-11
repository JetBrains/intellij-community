package org.intellij.plugins.markdown.ui.actions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware

internal abstract class ChangePreviewLayoutAction(
  private val layout: TextEditorWithPreview.Layout
): ToggleAction(layout.getName(), layout.getName(), layout.getIcon(null)), DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
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

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  class EditorOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR)

  class EditorAndPreview: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)

  class PreviewOnly: ChangePreviewLayoutAction(TextEditorWithPreview.Layout.SHOW_PREVIEW)
}
