package com.intellij.terminal.frontend

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toDto
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalInput(
  private val terminalSessionFuture: CompletableFuture<TerminalSession>,
  private val sessionModel: TerminalSessionModel,
  coroutineScope: CoroutineScope,
) {
  companion object {
    val DATA_KEY: DataKey<TerminalInput> = DataKey.Companion.create("TerminalInput")
    val KEY: Key<TerminalInput> = Key<TerminalInput>("TerminalInput")
  }

  /**
   * Use this channel to buffer the input events before we get the actual channel from the backend.
   */
  private val bufferChannel = Channel<TerminalInputEvent>(capacity = 10000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val inputChannelDeferred: Deferred<SendChannel<TerminalInputEvent>> =
    coroutineScope.async(Dispatchers.IO + CoroutineName("Get input channel")) {
      terminalSessionFuture.await().getInputChannel()
    }

  init {
    coroutineScope.launch {
      val targetChannel = inputChannelDeferred.await()

      try {
        for (event in bufferChannel) {
          targetChannel.send(event)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (_: ClosedSendChannelException) {
        thisLogger().warn("Failed to send the event because input channel is closed")
      }
      catch (t: Throwable) {
        thisLogger().error("Error while sending input event", t)
      }
    }
  }

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
    bufferChannel.trySend(event)
  }
}