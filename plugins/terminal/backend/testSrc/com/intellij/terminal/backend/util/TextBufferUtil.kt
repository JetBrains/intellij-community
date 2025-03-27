// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.util

import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.fus.BackendLatencyService
import org.jetbrains.plugins.terminal.fus.BackendOutputActivity

var backendOutputTestFusActivity: BackendOutputActivity? = null
  private set

fun startTestFusActivity() {
  backendOutputTestFusActivity = BackendLatencyService.getInstance().startBackendOutputActivity()
}

fun stopTestFusActivity() {
  backendOutputTestFusActivity = null
}

fun TerminalTextBuffer.write(text: String, y: Int, x: Int) {
  val fusActivity = checkNotNull(backendOutputTestFusActivity)
  fusActivity.charsRead(text.length)
  fusActivity.charProcessingStarted()
  fusActivity.charsProcessed(text.length)
  writeString(x, y, CharBuffer(text))
  fusActivity.charProcessingFinished()
}

/**
 * Scroll the screen buffer, so the [linesCount] lines from the top will be moved to history.
 */
fun TerminalTextBuffer.scrollDown(linesCount: Int) {
  val fusActivity = checkNotNull(backendOutputTestFusActivity)
  assert(linesCount >= 0) { "lines count can't be negative" }
  fusActivity.charsRead(1) // some imaginary "scroll down" control character
  fusActivity.charProcessingStarted()
  scrollArea(1, -linesCount, height)
  fusActivity.charProcessingFinished()
}