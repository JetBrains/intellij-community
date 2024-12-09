// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock

internal fun createTerminalOutputChannel(
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