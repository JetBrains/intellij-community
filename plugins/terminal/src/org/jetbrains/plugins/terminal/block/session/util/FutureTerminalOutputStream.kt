// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.util

import com.jediterm.terminal.TerminalOutputStream
import java.util.concurrent.CompletableFuture

/**
 * Allows operating with `CompletableFuture<TerminalOutputStream>` as if it was just `TerminalOutputStream`.
 * If the future is not resolved, then uses `thenAccept` to postpone the execution.
 */
internal class FutureTerminalOutputStream(
  private val future: CompletableFuture<out TerminalOutputStream?>,
) : TerminalOutputStream {

  @Volatile
  private var resolvedValue: TerminalOutputStream? = null

  @Volatile
  private var isResolved: Boolean = true

  init {
    future.thenAccept {
      resolvedValue = it
      isResolved = true
    }
  }

  override fun sendBytes(response: ByteArray, userInput: Boolean) {
    if (isResolved || resolvedValue != null) {
      resolvedValue?.sendBytes(response, userInput)
    }
    else {
      future.thenAccept { it?.sendBytes(response, userInput) }
    }
  }

  override fun sendString(string: String, userInput: Boolean) {
    if (isResolved || resolvedValue != null) {
      resolvedValue?.sendString(string, userInput)
    }
    else {
      future.thenAccept { it?.sendString(string, userInput) }
    }
  }
}
