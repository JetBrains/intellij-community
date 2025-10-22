// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.asDisposable
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.*
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.*

@ApiStatus.Internal
class TerminalShellIntegrationImpl(
  private val outputModel: TerminalOutputModel,
  sessionModel: TerminalSessionModel,
  coroutineScope: CoroutineScope,
) : TerminalShellIntegration {
  override val blocksModel: TerminalBlocksModelImpl = TerminalBlocksModelImpl(outputModel, sessionModel, coroutineScope.asDisposable())

  private val commandExecutionListeners = DisposableWrapperList<TerminalCommandExecutionListener>()

  override fun addCommandExecutionListener(parentDisposable: Disposable, listener: TerminalCommandExecutionListener) {
    commandExecutionListeners.add(listener, parentDisposable)
  }

  private val mutableOutputStatus = MutableStateFlow<TerminalOutputStatus>(WaitingForPrompt)
  override val outputStatus: StateFlow<TerminalOutputStatus> = mutableOutputStatus.asStateFlow()

  fun onPromptStarted(offset: TerminalOffset) {
    blocksModel.startNewBlock(offset)
    mutableOutputStatus.value = WaitingForPrompt
  }

  fun onPromptFinished(offset: TerminalOffset) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(commandStartOffset = offset)
    }
    mutableOutputStatus.value = TypingCommand
  }

  fun onCommandStarted(offset: TerminalOffset, command: String) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(outputStartOffset = offset, executedCommand = command)
    }
    mutableOutputStatus.value = ExecutingCommand

    val block = blocksModel.activeBlock as TerminalCommandBlock
    fireCommandExecutionListeners(TerminalCommandStartedEventImpl(outputModel, block))
  }

  fun onCommandFinished(exitCode: Int) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(exitCode = exitCode)
    }
    mutableOutputStatus.value = WaitingForPrompt

    val block = blocksModel.activeBlock as TerminalCommandBlock
    fireCommandExecutionListeners(TerminalCommandFinishedEventImpl(outputModel, block))
  }

  private fun fireCommandExecutionListeners(event: TerminalCommandExecutionEvent) {
    fireListenersAndLogAllExceptions(commandExecutionListeners, LOG, "Exception during handling $event") {
      when (event) {
        is TerminalCommandStartedEvent -> it.commandStarted(event)
        is TerminalCommandFinishedEvent -> it.commandFinished(event)
        else -> error("Unexpected event: $event")
      }
    }
  }

  companion object {
    private val LOG = logger<TerminalShellIntegrationImpl>()
  }
}

private data class TerminalCommandStartedEventImpl(
  override val outputModel: TerminalOutputModel,
  override val commandBlock: TerminalCommandBlock,
) : TerminalCommandStartedEvent

private data class TerminalCommandFinishedEventImpl(
  override val outputModel: TerminalOutputModel,
  override val commandBlock: TerminalCommandBlock,
) : TerminalCommandFinishedEvent