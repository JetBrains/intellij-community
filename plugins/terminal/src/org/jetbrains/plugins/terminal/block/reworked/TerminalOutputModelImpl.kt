// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TerminalTextHighlighter
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.block.ui.doTerminalOutputScrollChangingAction

/**
 * [maxOutputLength] limits the length of the editor document. Zero means unlimited length.
 */
internal class TerminalOutputModelImpl(
  override val editor: EditorEx,
  private val maxOutputLength: Int,
) : TerminalOutputModel {
  private val document = editor.document

  @VisibleForTesting
  val highlightingsModel = HighlightingsModel()

  private val mutableCursorOffsetState: MutableStateFlow<Int> = MutableStateFlow(0)
  override val cursorOffsetState: StateFlow<Int> = mutableCursorOffsetState.asStateFlow()

  @Volatile
  private var trimmedLinesCount: Int = 0
  private var trimmedCharsCount: Int = 0

  private var contentUpdateInProgress: Boolean = false

  init {
    editor.highlighter = TerminalTextHighlighter {
      // Highlightings are requested by the listeners of document change events during editor content updating.
      // But highlightings may be not in sync with the document content, so it may cause exceptions.
      // Also, there is no sense to provide a real highlighting until the update is finished, so return an empty snapshot in this case.
      if (contentUpdateInProgress) {
        TerminalOutputHighlightingsSnapshot(document, emptyList())
      }
      else highlightingsModel.getHighlightingsSnapshot()
    }
  }

  override fun updateContent(absoluteLineIndex: Int, text: String, styles: List<StyleRange>) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      editor.doTerminalOutputScrollChangingAction {
        contentUpdateInProgress = true
        try {
          // If absolute line index is far in the past - in the already trimmed part of the output,
          // then it means that the terminal was cleared, and we should reset to the initial state.
          if (absoluteLineIndex < trimmedLinesCount) {
            trimmedLinesCount = 0
            trimmedCharsCount = 0
          }

          val editorLineIndex = absoluteLineIndex - trimmedLinesCount
          doUpdateEditorContent(editorLineIndex, text, styles)
        }
        finally {
          contentUpdateInProgress = false
        }
      }
    }
  }

  override fun updateCursorPosition(absoluteLineIndex: Int, columnIndex: Int) {
    val editorLineIndex = absoluteLineIndex - trimmedLinesCount
    val newOffset = editor.logicalPositionToOffset(LogicalPosition(editorLineIndex, columnIndex))
    mutableCursorOffsetState.value = newOffset
  }

  @RequiresEdt
  private fun doUpdateEditorContent(editorLineIndex: Int, text: String, styles: List<StyleRange>) {
    if (editorLineIndex > 0 && editorLineIndex >= document.lineCount) {
      val newLines = "\n".repeat(editorLineIndex - document.lineCount + 1)
      document.insertString(document.textLength, newLines)
    }

    val replaceStartOffset = document.getLineStartOffset(editorLineIndex)
    document.replaceString(replaceStartOffset, document.textLength, text)

    highlightingsModel.removeAfter(replaceStartOffset)
    highlightingsModel.addHighlightings(replaceStartOffset, styles)

    editor.repaint(replaceStartOffset, document.textLength)

    trimToSize()
  }

  @RequiresEdt
  private fun trimToSize() {
    if (maxOutputLength > 0 && document.textLength > maxOutputLength) {
      trimToSize(maxOutputLength)
    }
  }

  @RequiresEdt
  private fun trimToSize(maxLength: Int) {
    val textLength = document.textLength
    check(textLength > maxLength) { "This method should be called only if text length $textLength is greater than max length $maxLength" }

    val lineCountBefore = document.lineCount
    val removeUntilOffset = textLength - maxLength
    document.deleteString(0, removeUntilOffset)

    highlightingsModel.removeBefore(removeUntilOffset)

    trimmedCharsCount += removeUntilOffset
    trimmedLinesCount += lineCountBefore - document.lineCount
  }

  @VisibleForTesting
  inner class HighlightingsModel {
    private val colorPalette: TerminalColorPalette = BlockTerminalColorPalette()

    /**
     * Contains sorted ranges of the text that are highlighted differently than default.
     * Indexes of the ranges are absolute to support trimming the start of the list
     * without reassigning indexes for the remaining ranges: [removeBefore].
     */
    private val highlightings: MutableList<HighlightingInfo> = ArrayDeque()

    /**
     * Contains sorted ranges of the highlightings that cover all document length.
     * Indexes of the ranges are editor-relative, so the first range always starts with 0.
     */
    private var highlightingsSnapshot: TerminalOutputHighlightingsSnapshot? = null

    @RequiresEdt
    fun getHighlightingsSnapshot(): TerminalOutputHighlightingsSnapshot {
      if (highlightingsSnapshot != null) {
        return highlightingsSnapshot!!
      }

      val editorRelativeHighlightings = highlightings.map {
        HighlightingInfo(it.startOffset - trimmedCharsCount, it.endOffset - trimmedCharsCount, it.textAttributesProvider)
      }
      val snapshot = TerminalOutputHighlightingsSnapshot(document, editorRelativeHighlightings)
      highlightingsSnapshot = snapshot
      return snapshot
    }

    @RequiresEdt
    fun addHighlightings(editorOffset: Int, styles: List<StyleRange>) {
      val absoluteOffset = editorOffset + trimmedCharsCount

      check(highlightings.isEmpty() || highlightings.last().endOffset <= absoluteOffset) { "New highlightings overlap with existing" }

      val newHighlightings = styles.map {
        HighlightingInfo(absoluteOffset + it.startOffset, absoluteOffset + it.endOffset, TextStyleAdapter(it.style, colorPalette))
      }
      highlightings.addAll(newHighlightings)

      highlightingsSnapshot = null
    }

    @RequiresEdt
    fun removeAfter(editorOffset: Int) {
      val absoluteOffset = editorOffset + trimmedCharsCount
      val highlightingIndex = highlightings.binarySearch { it.endOffset.compareTo(absoluteOffset) }
      val removeFromIndex = if (highlightingIndex < 0) -highlightingIndex - 1 else highlightingIndex + 1
      for (ind in (highlightings.size - 1) downTo removeFromIndex) {
        highlightings.removeAt(ind)
      }

      highlightingsSnapshot = null
    }

    @RequiresEdt
    fun removeBefore(editorOffset: Int) {
      val absoluteOffset = editorOffset + trimmedCharsCount
      val highlightingIndex = highlightings.binarySearch { it.startOffset.compareTo(absoluteOffset) }
      val removeUntilHighlightingIndex = if (highlightingIndex < 0) -highlightingIndex - 1 else highlightingIndex
      repeat(removeUntilHighlightingIndex) {
        highlightings.removeAt(0)
      }

      highlightingsSnapshot = null
    }
  }
}