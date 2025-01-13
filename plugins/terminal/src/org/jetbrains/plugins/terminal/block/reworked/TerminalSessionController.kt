// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.session.output.*
import java.awt.Toolkit
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val sessionModel: TerminalSessionModel,
  private val outputModel: TerminalOutputModel,
  private val alternateBufferModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  private val settings: JBTerminalSystemSettingsProviderBase,
  private val coroutineScope: CoroutineScope,
) {
  fun handleEvents(channel: ReceiveChannel<List<TerminalOutputEvent>>) {
    coroutineScope.launch {
      doHandleEvents(channel)
    }
  }

  private suspend fun doHandleEvents(channel: ReceiveChannel<List<TerminalOutputEvent>>) {
    for (events in channel) {
      for (event in events) {
        try {
          handleEvent(event)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
    }
  }

  private suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalContentUpdatedEvent -> updateOutputContent(event)
      is TerminalCursorPositionChangedEvent -> updateCursorPosition(event)
      is TerminalStateChangedEvent -> {
        val state = event.state.toTerminalState(settings.cursorShape)
        sessionModel.updateTerminalState(state)
      }
      is TerminalBeepEvent -> {
        if (settings.audibleBell()) {
          Toolkit.getDefaultToolkit().beep()
        }
      }
      TerminalShellIntegrationInitializedEvent -> {
        // TODO
      }
      TerminalPromptStartedEvent -> {
        withContext(Dispatchers.EDT) {
          blocksModel.promptStarted(outputModel.cursorOffsetState.value)
        }
      }
      TerminalPromptFinishedEvent -> {
        withContext(Dispatchers.EDT) {
          blocksModel.promptFinished(outputModel.cursorOffsetState.value)
        }
      }
      is TerminalCommandStartedEvent -> {
        withContext(Dispatchers.EDT) {
          blocksModel.commandStarted(outputModel.cursorOffsetState.value)
        }
      }
      is TerminalCommandFinishedEvent -> {
        withContext(Dispatchers.EDT) {
          blocksModel.commandFinished(event.exitCode)
        }
      }
    }
  }

  private suspend fun updateOutputContent(event: TerminalContentUpdatedEvent) {
    withContext(Dispatchers.EDT) {
      writeAction {
        val model = getCurrentOutputModel()
        model.updateContent(event.startLineLogicalIndex, event.text, event.styles)
      }
    }
  }

  private fun updateCursorPosition(event: TerminalCursorPositionChangedEvent) {
    val model = getCurrentOutputModel()
    model.updateCursorPosition(event.logicalLineIndex, event.columnIndex)
  }

  private fun getCurrentOutputModel(): TerminalOutputModel {
    return if (sessionModel.terminalState.value.isAlternateScreenBuffer) alternateBufferModel else outputModel
  }
}