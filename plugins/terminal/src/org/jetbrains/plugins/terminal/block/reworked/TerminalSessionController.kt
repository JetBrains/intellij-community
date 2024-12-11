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
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalOutputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalStateChangedEvent
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val model: TerminalModel,
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
      is TerminalContentUpdatedEvent -> updateEditorContent(event)
      is TerminalCursorPositionChangedEvent -> {
        withContext(Dispatchers.EDT) {
          model.updateCaretPosition(event.logicalLineIndex, event.columnIndex)
        }
      }
      is TerminalStateChangedEvent -> {
        val state = event.state.toTerminalState(settings.cursorShape)
        model.updateTerminalState(state)
      }
    }
  }

  private suspend fun updateEditorContent(event: TerminalContentUpdatedEvent) {
    withContext(Dispatchers.EDT) {
      writeAction {
        model.updateEditorContent(event.startLineLogicalIndex, event.text, event.styles)
      }
    }
  }
}