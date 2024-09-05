// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt.renderer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.*
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.block.session.ShellCommandOutputScraperImpl
import org.jetbrains.plugins.terminal.block.session.StyleRange
import org.jetbrains.plugins.terminal.block.session.StyledCommandOutput
import org.jetbrains.plugins.terminal.block.ui.normalize

internal class ShellPromptRenderer(
  private val colorPalette: TerminalColorPalette,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val terminalSizeProvider: () -> TermSize,
) : TerminalPromptRenderer {
  private val fallbackRenderer: TerminalPromptRenderer = BuiltInPromptRenderer(colorPalette, isSingleLine = false)

  override fun calculateRenderingInfo(state: TerminalPromptState): TerminalPromptRenderingInfo {
    val escapedPrompt = state.originalPrompt
    val escapedRightPrompt = state.originalRightPrompt
    if (escapedPrompt == null) {
      LOG.warn("Original prompt is null, falling back to built-in prompt. Prompt state: $state")
      return fallbackRenderer.calculateRenderingInfo(state)
    }
    val (prompt, rightPrompt) = if (escapedRightPrompt != null) {
      // Simple case, the right prompt is represented as a separate escape sequence.
      expandPrompt(escapedPrompt).content to expandPrompt(escapedRightPrompt).content
    }
    else {
      // There is a monolithic prompt, so we need to check that there is no meaningful after the cursor.
      // If there is something after the cursor - extract it as a right prompt.
      val promptInfo = expandPrompt(escapedPrompt)
      val lines = promptInfo.content.text.split("\n")
      assert(lines.size == promptInfo.cursorY + 1) { "Cursor is not at the last prompt line. Expanded prompt: $promptInfo, prompt state: $state" }
      assert(lines[promptInfo.cursorY].length >= promptInfo.cursorX) { "Cursor X position is out of prompt line length. Expanded prompt: $promptInfo, prompt state: $state" }
      val rightText = lines[promptInfo.cursorY].substring(promptInfo.cursorX)
      if (rightText.isNotBlank()) {
        val fullLines = lines.subList(0, lines.size - 1)
        val cursorOffset = fullLines.fold(0) { acc, line -> acc + line.length } + fullLines.size + promptInfo.cursorX
        val leftPart = promptInfo.content.subtext(0, cursorOffset)
        val rightPart = promptInfo.content.subtext(cursorOffset, promptInfo.content.text.length).trimStart()
        leftPart to rightPart
      }
      else {
        promptInfo.content to TextWithHighlightings("", emptyList())
      }
    }
    return TerminalPromptRenderingInfo(prompt.text, prompt.highlightings,
                                       rightPrompt.text, rightPrompt.highlightings)
  }

  private fun expandPrompt(escapedPrompt: String): ExpandedPromptInfo {
    val styleState = StyleState()
    val defaultStyle = TextStyle(TerminalColor { colorPalette.defaultForeground },
                                 TerminalColor { colorPalette.defaultBackground })
    styleState.setDefaultStyle(defaultStyle)
    val terminalSize = terminalSizeProvider()
    val textBuffer = TerminalTextBuffer(terminalSize.columns, terminalSize.rows, styleState, 0, null)
    val terminal = JediTerminal(FakeDisplay(settings), textBuffer, styleState)
    terminal.setModeEnabled(TerminalMode.AutoNewLine, true)
    val dataStream = ArrayTerminalDataStream(escapedPrompt.toCharArray())
    val emulator = JediEmulator(dataStream, terminal)

    while (emulator.hasNext()) {
      emulator.next()
    }

    val output: StyledCommandOutput = ShellCommandOutputScraperImpl.scrapeOutput(textBuffer)
    val highlightings = output.styleRanges.toHighlightings(output.text.length)
    val logicalPosition = textBuffer.screenBuffer.cursorToLogicalPosition(terminal.cursorX - 1, terminal.cursorY - 1)
    return ExpandedPromptInfo(TextWithHighlightings(output.text, highlightings), logicalPosition.column, logicalPosition.line)
  }

  private fun List<StyleRange>.toHighlightings(totalTextLength: Int): List<HighlightingInfo> {
    val highlightings = mutableListOf<HighlightingInfo>()
    var curOffset = 0
    for (range in this) {
      if (curOffset < range.startOffset) {
        highlightings.add(HighlightingInfo(curOffset, range.startOffset, EmptyTextAttributesProvider))
      }
      highlightings.add(HighlightingInfo(range.startOffset, range.endOffset, TextStyleAdapter(range.style, colorPalette)))
      curOffset = range.endOffset
    }
    if (curOffset < totalTextLength) {
      highlightings.add(HighlightingInfo(curOffset, totalTextLength, EmptyTextAttributesProvider))
    }
    return highlightings
  }

  // TODO: Add tests. Consider using it in TerminalCaretModel, because it is not taking into account the line wraps now.
  /**
   * [cursorX] and [cursorY] - the zero-based position of the cursor in the [LinesBuffer] grid.
   * @return a logical position of the cursor taking into account the line wraps in the grid.
   * If some lines are wrapped, we consider them as a single logical line.
   */
  private fun LinesBuffer.cursorToLogicalPosition(cursorX: Int, cursorY: Int): LogicalPosition {
    assert(cursorX >= 0 && cursorY >= 0)
    // Logical line is the number of non wrapped lines before the line with cursor
    var logicalLine = 0
    for (curY in 0 until cursorY) {
      if (!getLine(curY).isWrapped) {
        logicalLine++
      }
    }
    // Calculate the cumulative length of wrapped lines before the current line
    val wrappedLines = mutableListOf<TerminalLine>()
    var curY = cursorY - 1
    while (curY >= 0 && getLine(curY).isWrapped) {
      wrappedLines.add(getLine(curY))
      curY--
    }
    val wrappedLinesLength = wrappedLines.sumOf { line -> line.entries.sumOf { it.text.normalize().length } }
    // Logical column is the cumulative length of the wrapped lines before + the cursor offset in the current line
    val logicalColumn = wrappedLinesLength + cursorX
    return LogicalPosition(logicalLine, logicalColumn)
  }

  // TODO: Add tests
  private fun TextWithHighlightings.subtext(startOffset: Int, endOffset: Int): TextWithHighlightings {
    assert(startOffset in 0..endOffset && endOffset <= text.length)
    if (startOffset == endOffset) {
      return TextWithHighlightings("", emptyList())
    }
    val startHighlightingIndex = highlightings.indexOfFirst { startOffset in it.startOffset until it.endOffset }
    val endHighlightingIndex = highlightings.indexOfFirst { endOffset - 1 in it.startOffset until it.endOffset }
    assert(startHighlightingIndex >= 0 && endHighlightingIndex >= 0)
    val startHighlighting = highlightings[startHighlightingIndex]
    val endHighlighting = highlightings[endHighlightingIndex]
    val newHighlightings = if (startHighlightingIndex != endHighlightingIndex) {
      buildList {
        add(HighlightingInfo(startOffset, startHighlighting.endOffset, startHighlighting.textAttributesProvider))
        addAll(highlightings.subList(startHighlightingIndex + 1, endHighlightingIndex))
        add(HighlightingInfo(endHighlighting.startOffset, endOffset, endHighlighting.textAttributesProvider))
      }
    }
    else listOf(HighlightingInfo(startOffset, endOffset, startHighlighting.textAttributesProvider))

    val adjustedHighlightings = newHighlightings.rebase(-newHighlightings.first().startOffset)
    return TextWithHighlightings(text.substring(startOffset, endOffset), adjustedHighlightings)
  }

  // TODO: Add tests
  /** Removes blank text parts with default highlighting from the start */
  private fun TextWithHighlightings.trimStart(): TextWithHighlightings {
    if (text.isEmpty()) {
      return this
    }
    val newHighlightings = highlightings.dropWhile {
      text.substring(it.startOffset, it.endOffset).isBlank() && it.textAttributesProvider === EmptyTextAttributesProvider
    }
    if (newHighlightings.isEmpty()) {
      return TextWithHighlightings("", emptyList())
    }
    val startOffset = newHighlightings.first().startOffset
    val adjustedHighlightings = newHighlightings.rebase(-startOffset)
    return TextWithHighlightings(text.substring(startOffset), adjustedHighlightings)
  }

  private data class ExpandedPromptInfo(val content: TextWithHighlightings, val cursorX: Int, val cursorY: Int)

  private class FakeDisplay(private val settings: JBTerminalSystemSettingsProviderBase) : TerminalDisplay {
    override fun setCursor(x: Int, y: Int) {
      // do nothing
    }

    override fun setCursorShape(cursorShape: CursorShape?) {
      // do nothing
    }

    override fun beep() {
      // do nothing
    }

    override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {
      // do nothing
    }

    override fun setCursorVisible(isCursorVisible: Boolean) {
      // do nothing
    }

    override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {
      // do nothing
    }

    override fun getWindowTitle(): String {
      return ""
    }

    override fun setWindowTitle(windowTitle: String) {
      // do nothing
    }

    override fun getSelection(): TerminalSelection? {
      return null
    }

    override fun terminalMouseModeSet(mouseMode: MouseMode) {
      // do nothing
    }

    override fun setMouseFormat(mouseFormat: MouseFormat) {
      // do nothing
    }

    override fun ambiguousCharsAreDoubleWidth(): Boolean = settings.ambiguousCharsAreDoubleWidth()
  }

  companion object {
    private val LOG: Logger = logger<ShellPromptRenderer>()
  }
}
