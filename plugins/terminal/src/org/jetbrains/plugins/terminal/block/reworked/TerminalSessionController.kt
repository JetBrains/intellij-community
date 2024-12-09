// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalOutputEvent
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalSessionController(
  private val model: TerminalModel,
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
      is TerminalCursorPositionChangedEvent -> model.updateCaretPosition(event.logicalLineIndex, event.columnIndex)
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