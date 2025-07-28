package com.intellij.terminal.frontend

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
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
import org.jetbrains.plugins.terminal.fus.*
import java.awt.event.KeyEvent
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeMark

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalInput(
  private val terminalSessionFuture: CompletableFuture<TerminalSession>,
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
  private val bufferChannel = Channel<InputEventSubmission>(
    capacity = 10000,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val inputChannelDeferred: Deferred<SendChannel<TerminalInputEvent>> =
    coroutineScope.async(Dispatchers.IO + CoroutineName("Get input channel")) {
      terminalSessionFuture.await().getInputChannel()
    }

  private val typingLatencyReporter = BatchLatencyReporter(batchSize = 50) { samples ->
    ReworkedTerminalUsageCollector.logFrontendTypingLatency(
      totalDuration = samples.totalDuration(),
      duration90 = samples.percentile(90),
      secondLargestDuration = samples.secondLargest(),
    )
  }

  init {
    val job = coroutineScope.launch {
      val targetChannel = inputChannelDeferred.await()

      if (startupFusInfo != null) {
        // Report it only after receiving the input channel.
        // Only now we can consider that the shell is fully started, se we can send the input to it.
        reportShellStartingLatency(startupFusInfo)
      }

      try {
        for (submission in bufferChannel) {
          val event = submission.event
          targetChannel.send(event)

          val latency = submission.eventTime?.elapsedNow()
          if (latency != null) {
            typingLatencyReporter.update(latency)
          }

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

  fun sendString(data: String) {
    // TODO: should there always be UTF8?
    doSendBytes(data.toByteArray(StandardCharsets.UTF_8), eventTime = null)
  }

  /**
   * Sends the provided [data] and reports the typing latency from the moment of [eventTime].
   * This method should be used only for events triggered by the user.
   * For these events, we track the latency.
   */
  fun sendTrackedString(data: String, eventTime: TimeMark) {
    doSendBytes(data.toByteArray(StandardCharsets.UTF_8), eventTime)
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
    doSendBytes(data, eventTime = null)
  }

  fun sendEnter() {
    val enterBytes = encodingManager.getCode(KeyEvent.VK_ENTER, 0)!!
    sendBytes(enterBytes)
  }

  private fun doSendBytes(data: ByteArray, eventTime: TimeMark?) {
    val writeBytesEvent = TerminalWriteBytesEvent(bytes = data)
    sendEvent(InputEventSubmission(writeBytesEvent, eventTime))
  }

  fun sendClearBuffer() {
    sendEvent(InputEventSubmission(TerminalClearBufferEvent()))
  }

  /**
   * Note that resize events sent before the terminal session is initialized will be ignored.
   */
  fun sendResize(newSize: TermSize) {
    terminalSessionFuture.getNow(null) ?: return
    val event = TerminalResizeEvent(newSize.toDto())
    sendEvent(InputEventSubmission(event))
  }
  
  fun sendLinkClicked(isInAlternateBuffer: Boolean, hyperlinkId: TerminalHyperlinkId) {
    sendEvent(InputEventSubmission(TerminalHyperlinkClickedEvent(isInAlternateBuffer, hyperlinkId)))
  }

  private fun sendEvent(event: InputEventSubmission) {
    LOG.trace { "Input event received: ${event.event}" }

    val result = bufferChannel.trySend(event)

    if (result.isClosed) {
      LOG.warn("Terminal input channel is closed, $event won't be sent", result.exceptionOrNull())
    }
    else if (result.isFailure) {
      LOG.error("Failed to send input event: $event", result.exceptionOrNull())
    }
  }

  private fun reportShellStartingLatency(startupFusInfo: TerminalStartupFusInfo) {
    val latency = startupFusInfo.triggerTime.elapsedNow()
    ReworkedTerminalUsageCollector.logStartupShellStartingLatency(startupFusInfo.way, latency)
    LOG.info("Reworked terminal startup shell starting latency: ${latency.inWholeMilliseconds} ms")
  }

  private data class InputEventSubmission(
    val event: TerminalInputEvent,
    val eventTime: TimeMark? = null,
  )
}