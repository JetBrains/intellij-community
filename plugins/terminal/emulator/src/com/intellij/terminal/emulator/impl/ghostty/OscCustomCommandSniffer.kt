// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.terminal.emulator.impl.ghostty.OscCustomCommandSniffer.Companion.PREFIX
import com.intellij.util.io.UnsyncByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Supports custom commands emitted as non-standard OSC 1341 sequences.
 * Ghostty treats OSC 1341 as unknown and drops it, so this class scans the PTY byte
 * stream for `ESC ] 1341 ; arg1 ; ... ; argN <terminator>`.
 * Complete sequences are handed to [commandListener].
 *
 * [feed] also relays the stream to the emulator, splitting it at each command's terminator, so a listener
 * runs with everything that preceded its command already applied and nothing that follows it.
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

  /**
   * Scans [data] for commands and hands it to [writeToEmulator] as slices (`offset` and `length` into [data]).
   *
   * A command's terminator ends a slice: it and everything before it is written, then [commandListener] is
   * notified, then the rest of [data] follows. Every byte is relayed exactly once and in order.
   * [writeToEmulator] is called at least once, so an empty [data] still reaches the emulator.
   */
  fun feed(data: ByteArray, writeToEmulator: (offset: Int, length: Int) -> Unit) {
    var relayed = 0
    for (i in data.indices) {
      val command = feedByte(data[i]) ?: continue
      writeToEmulator(relayed, i + 1 - relayed)
      relayed = i + 1
      notifyListener(command)
    }
    if (relayed < data.size || data.isEmpty()) {
      writeToEmulator(relayed, data.size - relayed)
    }
  }

  /** Advances the match by [b], returning the command that [b] terminates, if any. */
  private fun feedByte(b: Byte): List<String>? {
    var command: List<String>? = null
    if (previousByteEscape) {
      if (b == BACKSLASH) {
        command = takeCommand()
      }
      reset()
      matchedPrefixCount = 1 // matched ESC
    }
    if (matchedPrefixCount > 0) {
      if (b == BEL) {
        command = takeCommand()
      }
      else {
        matchPrefixOrCollect(b)
      }
    }
    previousByteEscape = b == ESC
    return command
  }

  private fun matchPrefixOrCollect(b: Byte) {
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

  /** Resets the match, returning the collected command if a full [PREFIX] had been matched. */
  private fun takeCommand(): List<String>? {
    val content = if (isPrefixMatched()) buffer?.toString().orEmpty() else null // toString() decodes as UTF-8
    reset()
    return content?.split(';')
  }

  private fun notifyListener(command: List<String>) {
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
