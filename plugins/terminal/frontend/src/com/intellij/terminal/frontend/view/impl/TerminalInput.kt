package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.ui.sanitizeLineSeparators
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import java.awt.event.KeyEvent
import java.nio.charset.StandardCharsets
import kotlin.time.TimeMark

@ApiStatus.Internal
class TerminalInput(
  private val terminalSessionDeferred: Deferred<TerminalSession>,
  private val sessionModel: TerminalSessionModel,
  startupFusInfo: TerminalStartupFusInfo?,
  coroutineScope: CoroutineScope,
  private val encodingManager: TerminalKeyEncodingManager,
) {
  companion object {
    val DATA_KEY: DataKey<TerminalInput> = DataKey.Companion.create("TerminalInput")
    val KEY: Key<TerminalInput> = Key<TerminalInput>("TerminalInput")

    private val LOG = logger<TerminalInput>()
  }

  /**
   * Use this channel to buffer the input events before we get the actual channel from the backend.
   */
  private val bufferChannel = Channel<TerminalInputEvent>(
    capacity = 10000,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val inputChannelDeferred: Deferred<SendChannel<TerminalInputEvent>> =
    coroutineScope.async(CoroutineName("Get input channel")) {
      terminalSessionDeferred.await().getInputChannel()
    }

  init {
    val job = coroutineScope.launch {
      val targetChannel = inputChannelDeferred.await()

      if (startupFusInfo?.triggerTime != null) {
        // Report it only after receiving the input channel.
        // Only now we can consider that the shell is fully started, se we can send the input to it.
        reportShellStartingLatency(startupFusInfo.triggerTime!!, startupFusInfo.way)
      }

      try {
        for (event in bufferChannel) {
          targetChannel.send(event)

          LOG.trace { "Input event sent: $event" }
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (_: ClosedSendChannelException) {
        LOG.warn("Failed to send the event because input channel is closed")
      }
      catch (t: Throwable) {
        LOG.error("Error while sending input event", t)
      }
    }
    job.invokeOnCompletion {
      bufferChannel.close()
    }
  }

  fun sendText(options: TerminalSendTextOptions): Boolean {
    var text = options.text
    if (text.isEmpty()) {
      return false
    }
    val terminalState = sessionModel.terminalState.value
    if (options.requireBracketedPasteMode && !terminalState.isBracketedPasteMode) {
      return false
    }
    val endBytes = if (options.sendEndKeyBeforeText) encodingManager.getCode(KeyEvent.VK_END, 0) ?: return false else null
    if (endBytes != null) {
      sendBytes(endBytes)
    }

    text = sanitizeLineSeparators(text)

    if (options.useBracketedPasteMode && terminalState.isBracketedPasteMode) {
      text = "\u001b[200~$text\u001b[201~"
    }

    if (options.shouldExecute && !text.endsWith('\r')) {
      text += '\r'
    }

    sendString(text)
    return true
  }

  fun sendString(data: String) {
    // TODO: should there always be UTF8?
    doSendBytes(data.toByteArray(StandardCharsets.UTF_8))
  }

  fun sendBytes(data: ByteArray) {
    doSendBytes(data)
  }

  fun sendEnter() {
    val enterBytes = encodingManager.getCode(KeyEvent.VK_ENTER, 0)!!
    sendBytes(enterBytes)
  }

  fun sendLeft() {
    val leftBytes = encodingManager.getCode(KeyEvent.VK_LEFT, 0)!!
    sendBytes(leftBytes)
  }

  fun sendRight() {
    val rightBytes = encodingManager.getCode(KeyEvent.VK_RIGHT, 0)!!
    sendBytes(rightBytes)
  }

  private fun doSendBytes(data: ByteArray) {
    val writeBytesEvent = TerminalWriteBytesEvent(bytes = data)
    sendEvent(writeBytesEvent)
  }

  fun sendClearBuffer() {
    sendEvent(TerminalClearBufferEvent())
  }

  /**
   * Note that resize events sent before the terminal session is initialized will be ignored.
   */
  fun sendResize(newSize: TerminalGridSize) {
    terminalSessionDeferred.getNow() ?: return
    val event = TerminalResizeEvent(newSize)
    sendEvent(event)
  }

  private fun sendEvent(event: TerminalInputEvent) {
    LOG.trace { "Input event received: ${event}" }

    val result = bufferChannel.trySend(event)

    if (result.isClosed) {
      LOG.warn("Terminal input channel is closed, $event won't be sent", result.exceptionOrNull())
    }
    else if (result.isFailure) {
      LOG.error("Failed to send input event: $event", result.exceptionOrNull())
    }
  }

  private fun reportShellStartingLatency(triggerTime: TimeMark, openingWay: TerminalTabOpeningWay) {
    val latency = triggerTime.elapsedNow()
    ReworkedTerminalUsageCollector.logStartupShellStartingLatency(openingWay, latency)
    LOG.info("Reworked terminal startup shell starting latency: ${latency.inWholeMilliseconds} ms")
  }
}
