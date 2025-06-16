package com.intellij.terminal.backend

import com.intellij.idea.AppMode
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toTermSize
import com.jediterm.terminal.RequestOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.block.ui.withLock
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor
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
      BackendTerminalSession.LOG.error("Exception when handling input event: $event", t)
    }
  }
}

private fun handleInputEvent(event: TerminalInputEvent, services: JediTermServices) {
  val terminalStarter = services.terminalStarter

  when (event) {
    is TerminalWriteBytesEvent -> {
      val eventTime = TimeSource.Monotonic.markNow()

      if (isTypingBytes(event.bytes)) {
        terminalStarter.sendTypedBytes(event.bytes, eventTime)
      }
      else {
        terminalStarter.sendBytes(event.bytes, false)
      }

      // We count enter key presses on the backend separately, because it's used in the settings
      // to show the feedback notification, and the settings are currently shown on the backend through Lux.
      if (event.bytes.firstOrNull()?.toInt() == '\r'.code && AppMode.isRemoteDevHost()) {
        TerminalUsageLocalStorage.getInstance().recordEnterKeyPressed()
      }
    }
    is TerminalResizeEvent -> {
      terminalStarter.postResize(event.newSize.toTermSize(), RequestOrigin.User)
    }
    is TerminalCloseEvent -> {
      terminalStarter.close()
      terminalStarter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
        terminalStarter.requestEmulatorStop()
      }
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
          controller.y = 1
        }
      }
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