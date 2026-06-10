package com.intellij.terminal.frontend.session

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.ui.withLock
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.util.closeConnectorAndStopEmulation
import java.util.concurrent.CancellationException
import kotlin.time.TimeSource

internal fun createTerminalInputChannel(
  services: JediTermServices,
  coroutineScope: CoroutineScope,
): SendChannel<TerminalInputEvent> {
  val inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)

  coroutineScope.launch {
    try {
      handleInputEvents(inputChannel, services)
    }
    finally {
      inputChannel.close()
    }
  }

  return inputChannel
}

private suspend fun handleInputEvents(channel: ReceiveChannel<TerminalInputEvent>, services: JediTermServices) {
  for (event in channel) {
    try {
      handleInputEvent(event, services)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.error("Exception when handling input event: $event", t)
    }
  }
}

private suspend fun handleInputEvent(event: TerminalInputEvent, services: JediTermServices) {
  LOG.trace { "Input event received: $event" }

  val terminalStarter = services.terminalStarter

  TerminalActivityTracker.getInstance().registerActivity()

  when (event) {
    is TerminalWriteBytesEvent -> {
      val eventTime = TimeSource.Monotonic.markNow()

      if (isTypingBytes(event.bytes)) {
        terminalStarter.sendTypedBytes(event.bytes, eventTime)
      }
      else {
        terminalStarter.sendBytes(event.bytes, false)
      }
    }
    is TerminalResizeEvent -> {
      val termSize = TermSize(event.newSize.columns, event.newSize.rows)
      terminalStarter.postResize(termSize, RequestOrigin.User)
    }
    is TerminalCloseEvent -> {
      terminalStarter.closeConnectorAndStopEmulation()
    }
    is TerminalClearBufferEvent -> {
      val textBuffer = services.textBuffer
      textBuffer.withLock {
        textBuffer.clearHistory()
        // We need to clear the screen and keep the last line.
        // For some reason, it's necessary even if there's just one line,
        // otherwise the model ends up in a peculiar state
        // (the cursor jumps to the bottom-left corner of the empty screen).
        // But we can only keep the current line if the cursor position is valid to begin with.
        // The cursor is 1-based, the lines are 0-based (sic!),
        // so y > 0 means the cursor is valid, y - 1 stands for the line where the cursor is,
        // and in the end cursor should be on the first line, so y = 1.
        val controller = services.controller
        if (controller.y > 0) {
          val lastLine = textBuffer.getLine(controller.y - 1)
          textBuffer.clearScreenBuffer()
          textBuffer.addLine(lastLine)
          controller.linePositionAbsolute(1)
        }
      }
    }
    else -> {
      // Ignore unknown event
    }
  }
}

/**
 * Consider the byte sequence as typing if it contains the single character that is not a special symbol.
 */
private fun isTypingBytes(bytes: ByteArray): Boolean {
  val string = String(bytes, Charsets.UTF_8)
  if (string.length > 1) return false
  val char = string[0]
  return !Character.isISOControl(char) || char == '\r' || char.code == 127 // backspace (del)
}

private val LOG = fileLogger()