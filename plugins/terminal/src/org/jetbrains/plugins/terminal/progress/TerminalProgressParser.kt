// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.progress

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalProgressParser(private val progressHandler: (TerminalProgressState) -> Unit) {
  private var previousWasEscape = false
  private var oscBuffer: StringBuilder? = null
  private var escapeInOsc = false

  fun process(buf: CharArray, offset: Int, length: Int) {
    for (i in offset until offset + length) {
      processChar(buf[i])
    }
  }

  fun process(text: CharSequence) {
    for (c in text) {
      processChar(c)
    }
  }

  private fun processChar(c: Char) {
    val buffer = oscBuffer
    if (buffer == null) {
      processCharOutsideOsc(c)
      return
    }

    previousWasEscape = false
    if (c == STRING_TERMINATOR) {
      finishOsc(buffer)
      return
    }
    if (escapeInOsc) {
      if (c == ST_ESCAPE_END) {
        finishOsc(buffer)
        return
      }
      buffer.append(ESCAPE)
      escapeInOsc = false
    }

    when (c) {
      BEL -> finishOsc(buffer)
      ESCAPE -> escapeInOsc = true
      else -> {
        buffer.append(c)
        if (buffer.length > MAX_OSC_LENGTH) {
          resetOsc()
        }
      }
    }
  }

  private fun processCharOutsideOsc(c: Char) {
    if (c == OSC) {
      oscBuffer = StringBuilder()
      previousWasEscape = false
      return
    }

    if (previousWasEscape) {
      if (c == OSC_ESCAPE_START) {
        oscBuffer = StringBuilder()
        previousWasEscape = false
      }
      else {
        previousWasEscape = c == ESCAPE
      }
    }
    else {
      previousWasEscape = c == ESCAPE
    }
  }

  private fun finishOsc(buffer: StringBuilder) {
    val text = buffer.toString()
    resetOsc()
    parseProgressState(text)?.let(progressHandler)
  }

  private fun resetOsc() {
    oscBuffer = null
    escapeInOsc = false
    previousWasEscape = false
  }

  private fun parseProgressState(text: String): TerminalProgressState? {
    val parts = text.split(';')
    if (parts.size < 3 || parts[0] != "9" || parts[1] != "4") return null

    return when (parts[2].toIntOrNull()) {
      0 -> TerminalProgressState.NONE
      1 -> parseDeterminateProgress(parts, TerminalProgressState::normal)
      2 -> parseDeterminateProgress(parts, TerminalProgressState::error)
      3 -> TerminalProgressState.indeterminate()
      4 -> parseDeterminateProgress(parts, TerminalProgressState::warning)
      else -> null
    }
  }

  private fun parseDeterminateProgress(parts: List<String>, factory: (Int) -> TerminalProgressState): TerminalProgressState? {
    val percent = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 0..100 } ?: return null
    return factory(percent)
  }

  private companion object {
    const val ESCAPE: Char = '\u001B'
    const val BEL: Char = '\u0007'
    const val OSC_ESCAPE_START: Char = ']'
    const val ST_ESCAPE_END: Char = '\\'
    const val OSC: Char = '\u009D'
    const val STRING_TERMINATOR: Char = '\u009C'
    const val MAX_OSC_LENGTH: Int = 1024
  }
}
