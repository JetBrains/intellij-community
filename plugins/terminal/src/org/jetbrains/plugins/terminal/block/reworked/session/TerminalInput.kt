// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.openapi.actionSystem.DataKey
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

internal class TerminalInput(
  private val terminalSessionFuture: CompletableFuture<TerminalSession>,
) {
  companion object {
    val KEY: DataKey<TerminalInput> = DataKey.create("TerminalInput")
  }

  fun sendString(data: String) {
    // TODO: should there always be UTF8?
    sendBytes(data.toByteArray(StandardCharsets.UTF_8))
  }

  fun sendBytes(data: ByteArray) {
    terminalSessionFuture.thenAccept { session ->
      session?.inputChannel?.trySend(TerminalWriteBytesEvent(data))
    }
  }
}
