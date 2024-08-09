// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.block.session.scraper.DropTrailingNewLinesStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StylesCollectingTerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.scraper.CommandEndMarkerListeningStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.TerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.util.Debouncer
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

internal class ShellCommandOutputScraperImpl(
  private val textBuffer: TerminalTextBuffer,
  parentDisposable: Disposable,
  private val commandEndMarker: String?,
  private val debounceTimeout: Int = 50,
) : ShellCommandOutputScraper {

  constructor(session: BlockTerminalSession) : this(
    session.model.textBuffer,
    session as Disposable,
    session.commandBlockIntegration.commandEndMarker
  )

  private val listeners: MutableList<ShellCommandOutputListener> = CopyOnWriteArrayList()

  private val debouncer: Debouncer = Debouncer(debounceTimeout, parentDisposable)

  init {
    textBuffer.addModelListener(parentDisposable) {
      onContentChanged()
    }
  }

  /**
   * @param useExtendedDelayOnce whether to send first content update with greater delay than default
   */
  override fun addListener(listener: ShellCommandOutputListener, parentDisposable: Disposable, useExtendedDelayOnce: Boolean) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
    this.debouncer.setExtendedDelayOnce()
  }

  private fun onContentChanged() {
    if (listeners.isNotEmpty()) {
      debouncer.execute(::doOnContentChanged)
    }
  }

  private fun doOnContentChanged() {
    if (listeners.isNotEmpty()) {
      val output = scrapeOutput()
      for (listener in listeners) {
        listener.commandOutputChanged(output)
      }
    }
  }

  override fun scrapeOutput(): StyledCommandOutput = textBuffer.withLock {
    scrapeOutput(textBuffer, commandEndMarker)
  }

  companion object {
    fun scrapeOutput(session: BlockTerminalSession): StyledCommandOutput {
      return scrapeOutput(session.model.textBuffer, session.commandBlockIntegration.commandEndMarker)
    }

    fun scrapeOutput(textBuffer: TerminalTextBuffer, commandEndMarker: String? = null): StyledCommandOutput {
      var commandEndMarkerFound = false
      val stringCollector: StringCollector = CommandEndMarkerListeningStringCollector(
        DropTrailingNewLinesStringCollector(SimpleStringCollector()),
        commandEndMarker
      ) {
        commandEndMarkerFound = true
      }

      val styles: MutableList<StyleRange> = mutableListOf()
      val styleCollectingOutputBuilder: TerminalLinesCollector = StylesCollectingTerminalLinesCollector(stringCollector, styles::add)
      val terminalLinesCollector: TerminalLinesCollector = styleCollectingOutputBuilder
      textBuffer.collectLines(terminalLinesCollector)
      return StyledCommandOutput(stringCollector.buildText(), commandEndMarkerFound, styles)
    }
  }
}

internal data class StyledCommandOutput(val text: String, val commandEndMarkerFound: Boolean, val styleRanges: List<StyleRange>)
internal data class StyleRange(val startOffset: Int, val endOffset: Int, val style: TextStyle)

internal interface ShellCommandOutputListener {
  fun commandOutputChanged(output: StyledCommandOutput) {}
}

internal const val NEW_LINE: Char = '\n'
internal const val NEW_LINE_STRING: String = NEW_LINE.toString()

/**
 * Refines command output by dropping the trailing `\n` to avoid showing the last empty line in the command block.
 * Also, trims tailing whitespaces in case of Zsh: they are added to show '%' character at the end of the
 * last line without a newline.
 * Zsh adds the whitespaces after command finish and before calling `precmd` hook, so IDE cannot
 * identify correctly where command output ends exactly => trim tailing whitespaces as a workaround.

 * See `PROMPT_CR` and `PROMPT_SP` Zsh options, both are enabled by default:
 * https://zsh.sourceforge.io/Doc/Release/Options.html#Prompting
 *
 * Roughly, Zsh prints the following after each command and before prompt:
 * 1. `PROMPT_EOL_MARK` (by default, '%' for a normal user or a '#' for root)
 * 2. `$COLUMNS - 1` spaces
 * 3. \r
 * 4. A single space
 * 5. \r
 * https://github.com/zsh-users/zsh/blob/57248b88830ce56adc243a40c7773fb3825cab34/Src/utils.c#L1533-L1555
 *
 * Another workaround here is to add `unsetopt PROMPT_CR PROMPT_SP` to command-block-support.zsh,
 * but it will remove '%' mark on unterminated lines which can be unexpected for users.
 */
internal fun StyledCommandOutput.dropLastBlankLine(shellType: ShellType): StyledCommandOutput {
  val text = this.text
  val lastNewLineInd = text.lastIndexOf(NEW_LINE)
  val lastLine = text.substring(lastNewLineInd + 1)
  val outputEndsWithNewline = lastNewLineInd >= 0 && lastLine.isEmpty()
  val outputEndsWithWhitespacesForZsh = shellType == ShellType.ZSH && lastLine.isBlank()
  if (outputEndsWithNewline || outputEndsWithWhitespacesForZsh) {
    return trimToLength(this, max(0, lastNewLineInd))
  }
  return this
}

private fun trimToLength(styledCommandOutput: StyledCommandOutput, newLength: Int): StyledCommandOutput {
  if (newLength == styledCommandOutput.text.length) return styledCommandOutput
  return StyledCommandOutput(
    styledCommandOutput.text.substring(0, newLength),
    styledCommandOutput.commandEndMarkerFound,
    trimToLength(styledCommandOutput.styleRanges, newLength)
  )
}

private fun trimToLength(styleRanges: List<StyleRange>, newLength: Int): List<StyleRange> {
  return styleRanges.mapNotNull { trimToLength(it, newLength) }
}

private fun trimToLength(styleRange: StyleRange, newLength: Int): StyleRange? {
  // Style range fits inside new text - no trim required
  if (styleRange.endOffset <= newLength) {
    return styleRange
  }

  // Style range is out of result text
  if (styleRange.startOffset >= newLength) {
    return null
  }

  return StyleRange(styleRange.startOffset, newLength, styleRange.style)
}

internal fun TerminalTextBuffer.collectLines(
  terminalLinesCollector: TerminalLinesCollector,
) {
  if (!isUsingAlternateBuffer) {
    terminalLinesCollector.addLines(historyBuffer)
  }
  terminalLinesCollector.addLines(screenBuffer)
  terminalLinesCollector.flush()
}

internal fun TerminalTextBuffer.addModelListener(parentDisposable: Disposable, listener: TerminalModelListener) {
  TerminalUtil.addModelListener(this, parentDisposable, listener)
}
