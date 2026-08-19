// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.util.io.UnsyncByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Supports custom commands emitted as non-standard OSC 1341 sequences.
 * Ghostty treats OSC 1341 as unknown and drops it, so this class scans the PTY byte
 * stream for `ESC ] 1341 ; arg1 ; ... ; argN <terminator>`.
 * Complete sequences are handed to [commandListener].
 *
 * Matching state persists across [feed] calls, so a sequence may be split arbitrarily across chunks.
 * Listener exceptions are logged and ignored; cancellation and control-flow exceptions propagate.
 *
 * Not thread-safe: it mirrors the emulator's single-threaded contract.
 */
internal class OscCustomCommandSniffer(private val commandListener: TerminalCustomCommandListener) {
  /** How many leading bytes of [PREFIX] have been matched. */
  private var matchedPrefixCount: Int = 0

  /** The command collected after [PREFIX] */
  private var buffer: UnsyncByteArrayOutputStream? = null

  private var previousByteEscape: Boolean = false

  fun feed(data: ByteArray) {
    for (b in data) {
      if (previousByteEscape) {
        if (b == BACKSLASH) {
          finishAndReset()
        }
        reset()
        matchedPrefixCount = 1 // matched ESC
      }
      if (matchedPrefixCount > 0) {
        if (b == BEL) {
          finishAndReset()
        }
        else {
          processByte(b)
        }
      }
      previousByteEscape = b == ESC
    }
  }

  private fun processByte(b: Byte) {
    if (isPrefixMatched()) {
      appendBuffer(b)
    }
    else {
      if (b == PREFIX[matchedPrefixCount]) {
        matchedPrefixCount++
      }
      else {
        reset() // prefix match failed
      }
    }
  }

  private fun isPrefixMatched(): Boolean = matchedPrefixCount == PREFIX.size

  private fun appendBuffer(b: Byte) {
    if (b == ESC) {
      // Do not append ESC: it will either be dropped in case of `ESC \` terminator, or it will start a new escape sequence
      return
    }
    val buffer = this.buffer ?: UnsyncByteArrayOutputStream().also { this.buffer = it }
    if (buffer.size() >= MAX_BUFFER_BYTES) {
      reset()
    }
    else {
      buffer.write(b.toInt())
    }
  }

  private fun finishAndReset() {
    if (isPrefixMatched()) {
      val content = buffer?.toString().orEmpty() // toString() decodes as UTF-8
      val command = content.split(';')
      reset()
      try {
        commandListener.onCustomCommand(command)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        val message = "Custom terminal command listener failed"
        if (ApplicationManager.getApplication()?.isUnitTestMode == false) {
          LOG.error(message, e)
        }
        else {
          LOG.info(message, e)
        }
      }
    }
    else {
      reset()
    }
  }

  private fun reset() {
    matchedPrefixCount = 0
    // Dropping the buffer rather than reset()ing it: reset() only zeroes the count and keeps the backing
    // array, so one giant payload — a shell reporting its whole history at startup — would pin
    // MAX_BUFFER_BYTES for the terminal's life. Costs nothing: the next payload byte makes a new one.
    buffer = null
  }

  companion object {
    private val LOG: Logger = logger<OscCustomCommandSniffer>()

    private const val ESC: Byte = 0x1B
    private const val BEL: Byte = 0x07
    private const val BACKSLASH: Byte = '\\'.code.toByte()

    /**
     * What every custom command opens with: ESC, `]`, the JetBrains shell-integration command number
     * 1341, and the `;` that separates it from the arguments.
     */
    private val PREFIX: ByteArray = byteArrayOf(ESC) + "]1341;".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Upper bound on the custom commands sent by shell integration.
     */
    internal const val MAX_BUFFER_BYTES: Int = 1024 * 1024
  }
}
