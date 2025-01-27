// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.openapi.actionSystem.DataKey
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

@OptIn(ExperimentalCoroutinesApi::class)
internal class TerminalInput(
  private val terminalSessionFuture: CompletableFuture<TerminalSession>,
  private val sessionModel: TerminalSessionModel,
  private val coroutineScope: CoroutineScope,
) {
  companion object {
    val KEY: DataKey<TerminalInput> = DataKey.create("TerminalInput")
  }

  /**
   * Use this dispatcher to ensure that events are sent in the same order as methods in this class were called.
   */
  private val synchronousDispatcher = Dispatchers.Default.limitedParallelism(1)

  fun sendString(data: String) {
    // TODO: should there always be UTF8?
    sendBytes(data.toByteArray(StandardCharsets.UTF_8))
  }

  fun sendBracketedString(data: String) {
    if (sessionModel.terminalState.value.isBracketedPasteMode) {
      sendString("\u001b[200~$data\u001b[201~")
    }
    else {
      sendString(data)
    }
  }

  fun sendBytes(data: ByteArray) {
    withTerminalSession { session ->
      session.sendInputEvent(TerminalWriteBytesEvent(data))
    }
  }

  fun sendClearBuffer() {
    withTerminalSession { session ->
      session.sendInputEvent(TerminalClearBufferEvent)
    }
  }

  /**
   * Note that resize events sent before the terminal session is initialized will be ignored.
   */
  fun sendResize(newSize: TermSize) {
    val session = terminalSessionFuture.getNow(null) ?: return
    coroutineScope.launch(synchronousDispatcher) {
      session.sendInputEvent(TerminalResizeEvent(newSize))
    }
  }

  private fun withTerminalSession(block: suspend (TerminalSession) -> Unit) {
    terminalSessionFuture.thenAccept { session ->
      if (session != null) {
        coroutineScope.launch(synchronousDispatcher) {
          block(session)
        }
      }
    }
  }
}
