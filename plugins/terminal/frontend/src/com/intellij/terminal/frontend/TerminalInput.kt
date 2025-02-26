package com.intellij.terminal.frontend

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
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
    val KEY: DataKey<TerminalInput> = DataKey.Companion.create("TerminalInput")
  }

  private val inputChannelDeferred: Deferred<SendChannel<TerminalInputEvent>> =
    coroutineScope.async(Dispatchers.IO + CoroutineName("Get input channel")) {
      terminalSessionFuture.await().getInputChannel()
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
    sendEvent(TerminalWriteBytesEvent(data))
  }

  fun sendClearBuffer() {
    sendEvent(TerminalClearBufferEvent)
  }

  /**
   * Note that resize events sent before the terminal session is initialized will be ignored.
   */
  fun sendResize(newSize: TermSize) {
    terminalSessionFuture.getNow(null) ?: return
    sendEvent(TerminalResizeEvent(newSize.toDto()))
  }

  private fun sendEvent(event: TerminalInputEvent) {
    coroutineScope.launch(synchronousDispatcher + CoroutineName("Send input event")) {
      val channel = inputChannelDeferred.await()
      channel.send(event)
    }
  }
}