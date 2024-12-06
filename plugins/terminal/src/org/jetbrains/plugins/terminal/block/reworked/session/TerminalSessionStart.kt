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
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
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
  val outputChannel = createTerminalOutputChannel(services.textBuffer, services.terminalDisplay, outputScope)

  val inputScope = coroutineScope.childScope("Terminal input handling")
  val inputChannel = createTerminalInputChannel(services.terminalStarter, inputScope)

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
  val textBuffer = TerminalTextBuffer(termSize.columns, termSize.rows, styleState, maxHistoryLinesCount, null)
  val terminalDisplay = TerminalDisplayImpl()
  val controller = JediTerminal(terminalDisplay, textBuffer, styleState)
  val typeAheadManager = TerminalTypeAheadManager(JediTermTypeAheadModel(controller, textBuffer, settings))
  val executorService = TerminalExecutorServiceManagerImpl()
  val terminalStarter = TerminalStarter(
    controller,
    connector,
    TtyBasedArrayDataStream(connector),
    typeAheadManager,
    executorService
  )

  return JediTermServices(textBuffer, terminalDisplay, executorService, terminalStarter)
}

private fun createTerminalOutputChannel(
  textBuffer: TerminalTextBuffer,
  terminalDisplay: TerminalDisplayImpl,
  coroutineScope: CoroutineScope,
): ReceiveChannel<List<TerminalOutputEvent>> {
  val outputChannel = Channel<List<TerminalOutputEvent>>(capacity = Channel.UNLIMITED)

  val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
  val contentChangesTracker = TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
  val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

  coroutineScope.launch(Dispatchers.IO) {
    try {
      while (true) {
        textBuffer.withLock {
          val contentUpdate = contentChangesTracker.getContentUpdate()
          val cursorPositionUpdate = cursorPositionTracker.getCursorPositionUpdate()
          val updates = listOfNotNull(contentUpdate, cursorPositionUpdate)
          if (updates.isNotEmpty()) {
            outputChannel.send(updates)
          }
        }

        delay(10)
      }
    }
    finally {
      outputChannel.close()
    }
  }

  contentChangesTracker.addHistoryOverflowListener { contentUpdate ->
    val cursorPositionUpdate = cursorPositionTracker.getCursorPositionUpdate()
    val updates = listOfNotNull(contentUpdate, cursorPositionUpdate)
    if (updates.isNotEmpty()) {
      outputChannel.trySend(updates)
    }
  }

  return outputChannel
}

private fun createTerminalInputChannel(
  terminalStarter: TerminalStarter,
  coroutineScope: CoroutineScope,
): SendChannel<TerminalInputEvent> {
  val inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)

  coroutineScope.launch {
    for (event in inputChannel) {
      when (event) {
        is TerminalWriteStringEvent -> {
          terminalStarter.sendString(event.string, false)
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
  val executorService: TerminalExecutorServiceManager,
  val terminalStarter: TerminalStarter,
)
