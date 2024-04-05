// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.exp.prompt.BuiltInPromptRenderer
import org.jetbrains.plugins.terminal.exp.prompt.PromptRenderingInfo
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptRenderer
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptState

class ShellPromptRenderer(private val session: BlockTerminalSession) : TerminalPromptRenderer {
  private val fallbackRenderer: TerminalPromptRenderer = BuiltInPromptRenderer(session)

  override fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo {
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
    return PromptRenderingInfo(prompt.text, prompt.highlightings,
                               rightPrompt.text, rightPrompt.highlightings)
  }

  private fun expandPrompt(escapedPrompt: String): ExpandedPromptInfo {
    val styleState = StyleState()
    val defaultStyle = TextStyle(TerminalColor { session.colorPalette.defaultForeground },
                                 TerminalColor { session.colorPalette.defaultBackground })
    styleState.setDefaultStyle(defaultStyle)
    val (width, height) = session.model.withContentLock { session.model.width to session.model.height }
    val textBuffer = TerminalTextBuffer(width, height, styleState, 0, null)
    val terminal = JediTerminal(FakeDisplay(session.settings), textBuffer, styleState)
    terminal.setModeEnabled(TerminalMode.AutoNewLine, true)
    val dataStream = ArrayTerminalDataStream(escapedPrompt.toCharArray())
    val emulator = JediEmulator(dataStream, terminal)

    while (emulator.hasNext()) {
      emulator.next()
    }

    val output: StyledCommandOutput = ShellCommandOutputScraper.scrapeOutput(textBuffer)
    val highlightings = output.styleRanges.toHighlightings(output.text.length)
    return ExpandedPromptInfo(TextWithHighlightings(output.text, highlightings), terminal.cursorX - 1, terminal.cursorY - 1)
  }

  private fun List<StyleRange>.toHighlightings(totalTextLength: Int): List<HighlightingInfo> {
    val highlightings = mutableListOf<HighlightingInfo>()
    var curOffset = 0
    for (range in this) {
      if (curOffset < range.startOffset) {
        highlightings.add(HighlightingInfo(curOffset, range.startOffset, EmptyTextAttributesProvider))
      }
      highlightings.add(HighlightingInfo(range.startOffset, range.endOffset, TextStyleAdapter(range.style, session.colorPalette)))
      curOffset = range.endOffset
    }
    if (curOffset < totalTextLength) {
      highlightings.add(HighlightingInfo(curOffset, totalTextLength, EmptyTextAttributesProvider))
    }
    return highlightings
  }

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

    override fun setCursorShape(cursorShape: CursorShape) {
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

    override fun setBlinkingCursor(isCursorBlinking: Boolean) {
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