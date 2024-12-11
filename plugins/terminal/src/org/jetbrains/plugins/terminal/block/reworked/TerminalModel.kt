// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
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
import org.jetbrains.plugins.terminal.block.ui.doWithScrollingAware

/**
 * [maxOutputLength] limits the length of the editor document. Zero means unlimited length.
 */
internal class TerminalModel(
  val editor: EditorEx,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val maxOutputLength: Int,
) {
  private val document = editor.document

  private val colorPalette: TerminalColorPalette = BlockTerminalColorPalette()
  private val highlightings: MutableList<HighlightingInfo> = ArrayDeque()
  private var highlightingsSnapshot: TerminalOutputHighlightingsSnapshot? = null

  private var trimmedCharsCount: Int = 0
  private var trimmedLinesCount: Int = 0

  private val mutableCaretOffsetState = MutableStateFlow(0)
  val caretOffsetState: StateFlow<Int> = mutableCaretOffsetState.asStateFlow()

  private val mutableTerminalStateFlow = MutableStateFlow(getInitialState())
  val terminalState: StateFlow<TerminalState> = mutableTerminalStateFlow.asStateFlow()

  init {
    editor.highlighter = TerminalTextHighlighter { getHighlightingsSnapshot() }
  }

  @RequiresEdt
  @RequiresWriteLock
  fun updateEditorContent(absoluteLineIndex: Int, text: String, styles: List<StyleRange>) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      editor.doWithScrollingAware {
        DocumentUtil.executeInBulk(document) {
          val editorLineIndex = absoluteLineIndex - trimmedLinesCount
          doUpdateEditorContent(editorLineIndex, text, styles)
        }
      }
    }
  }

  @RequiresEdt
  fun updateCaretPosition(absoluteLineIndex: Int, columnIndex: Int) {
    val editorLineIndex = absoluteLineIndex - trimmedLinesCount
    val newOffset = editor.logicalPositionToOffset(LogicalPosition(editorLineIndex, columnIndex))
    mutableCaretOffsetState.value = newOffset
  }

  fun updateTerminalState(state: TerminalState) {
    mutableTerminalStateFlow.value = state
  }

  private fun getInitialState(): TerminalState {
    return TerminalState(
      isCursorVisible = true,
      cursorShape = settings.cursorShape,
      mouseMode = MouseMode.MOUSE_REPORTING_NONE,
      mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM,
      isAlternateScreenBuffer = false,
      isApplicationArrowKeys = false,
      isApplicationKeypad = false,
      isAutoNewLine = false,
      isAltSendsEscape = true,
      isBracketedPasteMode = false,
      windowTitle = ""
    )
  }

  @RequiresEdt
  private fun doUpdateEditorContent(editorLineIndex: Int, text: String, styles: List<StyleRange>) {
    if (editorLineIndex >= document.lineCount && document.textLength > 0) {
      val newLines = "\n".repeat(editorLineIndex - document.lineCount + 1)
      document.insertString(document.textLength, newLines)
    }

    val replaceStartOffset = document.getLineStartOffset(editorLineIndex)
    document.replaceString(replaceStartOffset, document.textLength, text)

    updateHighlightings(replaceStartOffset, styles)

    trimToSize()
  }

  @RequiresEdt
  private fun updateHighlightings(editorStartOffset: Int, styles: List<StyleRange>) {
    val absoluteStartOffset = editorStartOffset + trimmedCharsCount

    // Remove previous highlightings that are overlapped by the new highlightings.
    val highlightingIndex = highlightings.binarySearch { it.endOffset.compareTo(absoluteStartOffset) }
    val removeFromIndex = if (highlightingIndex < 0) -highlightingIndex - 1 else highlightingIndex + 1
    for (ind in (highlightings.size - 1) downTo removeFromIndex) {
      highlightings.removeAt(ind)
    }

    val replaceHighlightings = styles.map {
      HighlightingInfo(absoluteStartOffset + it.startOffset, absoluteStartOffset + it.endOffset, TextStyleAdapter(it.style, colorPalette))
    }
    highlightings.addAll(replaceHighlightings)

    highlightingsSnapshot = null
  }

  @VisibleForTesting
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

  private fun trimToSize() {
    if (maxOutputLength > 0 && document.textLength > maxOutputLength) {
      trimToSize(maxOutputLength)
    }
  }

  private fun trimToSize(maxLength: Int) {
    val textLength = document.textLength
    check(textLength > maxLength) { "This method should be called only if text length $textLength is greater than max length $maxLength" }

    val lineCountBefore = document.lineCount
    val removeUntilOffset = textLength - maxLength
    document.deleteString(0, removeUntilOffset)

    val absoluteRemoveUntilOffset = removeUntilOffset + trimmedCharsCount
    val highlightingIndex = highlightings.binarySearch { it.startOffset.compareTo(absoluteRemoveUntilOffset) }
    val removeUntilHighlightingIndex = if (highlightingIndex < 0) -highlightingIndex - 1 else highlightingIndex
    repeat(removeUntilHighlightingIndex) {
      highlightings.removeAt(0)
    }
    highlightingsSnapshot = null

    trimmedCharsCount += removeUntilOffset
    trimmedLinesCount += lineCountBefore - document.lineCount
  }
}