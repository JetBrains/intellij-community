package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel

internal data class MarkdownEditorWithPreviewState(
  val underlyingState: FileEditorState,
  val isVerticalSplit: Boolean
): FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is MarkdownEditorWithPreviewState && underlyingState.canBeMergedWith(
      otherState.underlyingState,
      level
    )
  }
}
