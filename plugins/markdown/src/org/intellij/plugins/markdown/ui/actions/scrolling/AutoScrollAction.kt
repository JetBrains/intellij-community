package org.intellij.plugins.markdown.ui.actions.scrolling

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownSplitEditor
import org.intellij.plugins.markdown.ui.split.SplitFileEditor

class AutoScrollAction : ToggleAction(), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    val splitFileEditor = MarkdownActionUtil.findSplitEditor(e)
    if (splitFileEditor !is MarkdownSplitEditor) return false

    e.presentation.isEnabled = splitFileEditor.currentEditorLayout == SplitFileEditor.SplitEditorLayout.SPLIT

    return splitFileEditor.isAutoScrollPreview
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = MarkdownActionUtil.findSplitEditor(e) is MarkdownSplitEditor
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val splitFileEditor = MarkdownActionUtil.findSplitEditor(e)
    if (splitFileEditor !is MarkdownSplitEditor) return

    splitFileEditor.isAutoScrollPreview = state
  }
}