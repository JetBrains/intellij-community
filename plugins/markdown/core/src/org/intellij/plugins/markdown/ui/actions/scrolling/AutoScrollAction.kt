package org.intellij.plugins.markdown.ui.actions.scrolling

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview

class AutoScrollAction : ToggleAction(), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    val splitFileEditor = MarkdownActionUtil.findSplitEditor(e)
    if (splitFileEditor == null) return false

    e.presentation.isEnabled = splitFileEditor.layout == TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW

    return splitFileEditor.isAutoScrollPreview
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = MarkdownActionUtil.findSplitEditor(e) is MarkdownEditorWithPreview
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val splitFileEditor = MarkdownActionUtil.findSplitEditor(e)
    splitFileEditor?.isAutoScrollPreview = state
  }
}