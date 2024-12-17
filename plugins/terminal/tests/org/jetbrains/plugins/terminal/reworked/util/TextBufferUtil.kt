// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked.util

import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalTextBuffer

internal fun TerminalTextBuffer.write(text: String, y: Int, x: Int) {
  writeString(x, y, CharBuffer(text))
}

/**
 * Scroll the screen buffer, so the [linesCount] lines from the top will be moved to history.
 */
internal fun TerminalTextBuffer.scrollDown(linesCount: Int) {
  assert(linesCount >= 0) { "lines count can't be negative" }
  scrollArea(1, -linesCount, height)
}