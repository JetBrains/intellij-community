package com.intellij.terminal.backend

import com.intellij.openapi.Disposable
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class TerminalOutputLatencyTracker(
  ttyConnector: ObservableTtyConnector,
  textBuffer: TerminalTextBuffer,
  parentDisposable: Disposable,
) {
  // Variables are guarded by TerminalTextBuffer lock

  /**
   * Last moment when we read some bytes from the TTY.
   * Guarded by TerminalTextBuffer lock.
   */
  private var lastTtyReadTime: TimeMark? = null

  /**
   * First moment of reading the bytes from the TTY, that caused changing the text buffer.
   * Guarded by TerminalTextBuffer lock.
   */
  private var firstChangeTtyReadTime: TimeMark? = null

  init {
    ttyConnector.addListener(parentDisposable, object : TtyConnectorListener {
      override fun charsRead(buf: CharArray, offset: Int, length: Int) {
        lastTtyReadTime = TimeSource.Monotonic.markNow()
      }
    })

    textBuffer.addChangesListener(object : TextBufferChangesListener {
      override fun linesChanged(fromIndex: Int) {
        if (firstChangeTtyReadTime == null) {
          firstChangeTtyReadTime = lastTtyReadTime
        }
      }
    })
  }

  /**
   * Returns the first time of reading bytes from the TTY that corresponds
   * to the currently uncollected changes in the text buffer.
   * And then resets the timer, so the current text buffer changes are considered as collected.
   */
  fun getCurUpdateTtyReadTimeAndReset(): TimeMark? {
    val result = firstChangeTtyReadTime
    firstChangeTtyReadTime = null
    return result
  }
}