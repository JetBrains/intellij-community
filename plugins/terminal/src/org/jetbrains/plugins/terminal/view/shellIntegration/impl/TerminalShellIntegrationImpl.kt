// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.impl.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.*
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.*

@ApiStatus.Internal
class TerminalShellIntegrationImpl(
  private val outputModel: TerminalOutputModel,
  sessionModel: TerminalSessionModel,
  parentDisposable: Disposable,
) : TerminalShellIntegration {
  override val blocksModel: TerminalBlocksModelImpl = TerminalBlocksModelImpl(outputModel, sessionModel, parentDisposable)

  private val commandExecutionListeners = DisposableWrapperList<TerminalCommandExecutionListener>()

  override fun addCommandExecutionListener(parentDisposable: Disposable, listener: TerminalCommandExecutionListener) {
    commandExecutionListeners.add(listener, parentDisposable)
  }

  private val mutableOutputStatus = MutableStateFlow<TerminalOutputStatus>(WaitingForPrompt)
  override val outputStatus: StateFlow<TerminalOutputStatus> = mutableOutputStatus.asStateFlow()

  private val completionListeners = DisposableWrapperList<TerminalShellBasedCompletionListener>()

  override fun addShellBasedCompletionListener(parentDisposable: Disposable, listener: TerminalShellBasedCompletionListener) {
    completionListeners.add(listener, parentDisposable)
  }

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

  fun onCompletionFinished(result: String) {
    fireListenersAndLogAllExceptions(completionListeners, LOG, "Exception during handling completion finished event") {
      it.completionFinished(result)
    }
  }

  fun restoreFromState(blocksModelState: TerminalBlocksModelState) {
    blocksModel.restoreFromState(blocksModelState)

    val activeBlock = blocksModel.activeBlock as TerminalCommandBlock
    mutableOutputStatus.value = when {
      activeBlock.commandStartOffset == null -> WaitingForPrompt
      activeBlock.commandStartOffset != null && activeBlock.outputStartOffset == null -> TypingCommand
      activeBlock.outputStartOffset != null -> ExecutingCommand
      else -> {
        // shouldn't be possible with the conditions above, but let's add protection for possible changes
        LOG.warn("Unexpected state of blocks model: $blocksModelState")
        WaitingForPrompt
      }
    }
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