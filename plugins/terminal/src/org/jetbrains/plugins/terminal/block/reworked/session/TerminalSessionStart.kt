// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.model.JediTermTypeAheadModel
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.session.output.ObservableJediTerminal
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalDisplayImpl
import org.jetbrains.plugins.terminal.block.reworked.session.output.createTerminalOutputChannel
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor

private val LOG: Logger = Logger.getInstance(TerminalSession::class.java)

internal fun startTerminalSession(
  connector: TtyConnector,
  termSize: TermSize,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val maxHistoryLinesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count")
  val services: JediTermServices = createJediTermServices(connector, termSize, maxHistoryLinesCount, settings)

  val outputScope = coroutineScope.childScope("Terminal output forwarding")
  val outputChannel = createTerminalOutputChannel(services.textBuffer, services.terminalDisplay, services.controller, outputScope)

  val inputScope = coroutineScope.childScope("Terminal input handling")
  val inputChannel = createTerminalInputChannel(services, inputScope)

  services.executorService.unboundedExecutorService.submit {
    try {
      startTerminalEmulation(services.terminalStarter)
    }
    finally {
      coroutineScope.cancel()
    }
  }

  coroutineScope.coroutineContext.job.invokeOnCompletion {
    val starter = services.terminalStarter
    starter.close() // close in background
    //starter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
    //  starter.requestEmulatorStop()
    //}
  }

  return TerminalSessionImpl(inputChannel, outputChannel)
}

private fun createJediTermServices(
  connector: TtyConnector,
  termSize: TermSize,
  maxHistoryLinesCount: Int,
  settings: JBTerminalSystemSettingsProviderBase,
): JediTermServices {
  val styleState = StyleState()
  val textBuffer = TerminalTextBuffer(termSize.columns, termSize.rows, styleState, maxHistoryLinesCount)
  val terminalDisplay = TerminalDisplayImpl(settings)
  val controller = ObservableJediTerminal(terminalDisplay, textBuffer, styleState)
  val typeAheadManager = TerminalTypeAheadManager(JediTermTypeAheadModel(controller, textBuffer, settings))
  val executorService = TerminalExecutorServiceManagerImpl()
  val terminalStarter = TerminalStarter(
    controller,
    connector,
    TtyBasedArrayDataStream(connector),
    typeAheadManager,
    executorService
  )

  return JediTermServices(textBuffer, terminalDisplay, controller, executorService, terminalStarter)
}

private fun createTerminalInputChannel(
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
          terminalStarter.postResize(event.newSize, RequestOrigin.User)
        }
        is TerminalCloseEvent -> {
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

private fun startTerminalEmulation(terminalStarter: TerminalStarter) {
  try {
    terminalStarter.start()
  }
  catch (t: Throwable) {
    LOG.error(t)
  }
  finally {
    try {
      terminalStarter.ttyConnector.close()
    }
    catch (t: Throwable) {
      LOG.error("Error closing TtyConnector", t)
    }
  }
}

private class JediTermServices(
  val textBuffer: TerminalTextBuffer,
  val terminalDisplay: TerminalDisplayImpl,
  val controller: ObservableJediTerminal,
  val executorService: TerminalExecutorServiceManager,
  val terminalStarter: TerminalStarter,
)
