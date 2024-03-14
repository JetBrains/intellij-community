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
    if (escapedPrompt == null) {
      LOG.warn("Original prompt is null, falling back to built-in prompt. Prompt state: $state")
      return fallbackRenderer.calculateRenderingInfo(state)
    }
    val expandedPrompt = expandPrompt(escapedPrompt)
    val expandedRightPrompt = state.originalRightPrompt?.let { expandPrompt(it) } ?: RenderingInfo("", emptyList())
    return PromptRenderingInfo(expandedPrompt.text, expandedPrompt.highlightings,
                               expandedRightPrompt.text, expandedRightPrompt.highlightings)
  }

  private fun expandPrompt(escapedPrompt: String): RenderingInfo {
    val styleState = StyleState()
    val defaultStyle = TextStyle(TerminalColor { session.colorPalette.defaultForeground },
                                 TerminalColor { session.colorPalette.defaultBackground })
    styleState.setDefaultStyle(defaultStyle)
    val textBuffer = TerminalTextBuffer(session.model.width, session.model.height, styleState, 0, null)
    val terminal = JediTerminal(FakeDisplay(session.settings), textBuffer, styleState)
    terminal.setModeEnabled(TerminalMode.AutoNewLine, true)
    val dataStream = ArrayTerminalDataStream(escapedPrompt.toCharArray())
    val emulator = JediEmulator(dataStream, terminal)

    while (emulator.hasNext()) {
      emulator.next()
    }

    val output: StyledCommandOutput = ShellCommandOutputScraper.scrapeOutput(textBuffer)
    val highlightings = output.styleRanges.toHighlightings(output.text.length)
    return RenderingInfo(output.text, highlightings)
  }

  private data class RenderingInfo(val text: String, val highlightings: List<HighlightingInfo>)

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