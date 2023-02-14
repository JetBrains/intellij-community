package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.split.SplitTextEditorProvider
import org.jdom.DataConversionException
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSplitEditorProvider: SplitTextEditorProvider(PsiAwareTextEditorProvider(), MarkdownPreviewFileEditorProvider()) {
  override fun createSplitEditor(firstEditor: FileEditor, secondEditor: FileEditor): FileEditor {
    require(firstEditor is TextEditor) { "Main editor should be TextEditor" }
    require(secondEditor is MarkdownPreviewFileEditor) { "Secondary editor should be MarkdownPreviewFileEditor" }
    return MarkdownEditorWithPreview(firstEditor, secondEditor)
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    val underlyingState = readUnderlyingState(sourceElement, project, file)
    val isVerticalSplit = sourceElement.getAttribute(VERTICAL_SPLIT)?.let {
      try {
        it.booleanValue
      }
      catch (ignored: DataConversionException) {
        false
      }
    } ?: false
    return MarkdownEditorWithPreviewState(underlyingState, isVerticalSplit)
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is MarkdownEditorWithPreviewState) {
      writeUnderlyingState(state.underlyingState, project, targetElement)
      targetElement.setAttribute(VERTICAL_SPLIT, state.isVerticalSplit.toString())
    }
  }

  private fun readLayoutState(sourceElement: Element, project: Project, file: VirtualFile): TextEditorWithPreview.Layout? {
    val value = readSplitLayoutState(sourceElement, project, file)
    if (value != null) {
      try {
        return TextEditorWithPreview.Layout.valueOf(value)
      }
      catch (ignored: IllegalArgumentException) {
      }
    }
    return null
  }

  private fun readUnderlyingState(sourceElement: Element, project: Project, file: VirtualFile): TextEditorWithPreview.MyFileEditorState {
    val firstState = readFirstProviderState(sourceElement, project, file)
    val secondState = readSecondProviderState(sourceElement, project, file)
    val layoutState = readLayoutState(sourceElement, project, file)
    return TextEditorWithPreview.MyFileEditorState(layoutState, firstState, secondState)
  }

  private fun writeSplitLayoutState(layout: TextEditorWithPreview.Layout?, project: Project, targetElement: Element) {
    val value = layout?.name ?: return
    writeSplitLayoutState(value, project, targetElement)
  }

  private fun writeUnderlyingState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is TextEditorWithPreview.MyFileEditorState) {
      writeFirstProviderState(state.firstState, project, targetElement)
      writeSecondProviderState(state.secondState, project, targetElement)
      writeSplitLayoutState(state.splitLayout, project, targetElement)
    }
  }

  companion object {
    private const val VERTICAL_SPLIT = "is_vertical_split"
  }
}
