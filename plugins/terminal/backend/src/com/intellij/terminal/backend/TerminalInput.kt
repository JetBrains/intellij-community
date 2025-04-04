package com.intellij.terminal.backend

import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toTermSize
import com.jediterm.terminal.RequestOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.ui.withLock
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor

internal fun createTerminalInputChannel(
  services: JediTermServices,
  coroutineScope: CoroutineScope,
): SendChannel<TerminalInputEvent> {
  val terminalStarter = services.terminalStarter
  val textBuffer = services.textBuffer
  val controller = services.controller
  val inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)

  coroutineScope.launch {
    for (event in inputChannel) {
      when (event) {
        is TerminalWriteBytesEvent -> {
          terminalStarter.sendBytes(event.bytes, false)
        }
        is TerminalResizeEvent -> {
          terminalStarter.postResize(event.newSize.toTermSize(), RequestOrigin.User)
        }
        TerminalCloseEvent -> {
          terminalStarter.close()
          terminalStarter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
            terminalStarter.requestEmulatorStop()
          }
        }
        is TerminalClearBufferEvent -> {
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
  }

  return inputChannel
}