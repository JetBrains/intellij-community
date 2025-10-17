// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.impl

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.*
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.*

@ApiStatus.Internal
class TerminalShellIntegrationImpl(
  private val outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
) : TerminalShellIntegration {
  override val blocksModel: TerminalBlocksModelImpl = TerminalBlocksModelImpl(outputModel, coroutineScope.asDisposable())

  private val dispatcher = EventDispatcher.create(TerminalCommandExecutionListener::class.java)

  override fun addCommandExecutionListener(parentDisposable: Disposable, listener: TerminalCommandExecutionListener) {
    dispatcher.addListener(listener, parentDisposable)
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

  fun onCommandStarted(offset: TerminalOffset) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(outputStartOffset = offset)
    }
    mutableOutputStatus.value = ExecutingCommand

    val block = blocksModel.activeBlock as TerminalCommandBlock
    dispatcher.multicaster.commandStarted(TerminalCommandStartedEventImpl(outputModel, block))
  }

  fun onCommandFinished(exitCode: Int) {
    blocksModel.updateActiveCommandBlock { block ->
      block.copy(exitCode = exitCode)
    }
    mutableOutputStatus.value = WaitingForPrompt

    val block = blocksModel.activeBlock as TerminalCommandBlock
    dispatcher.multicaster.commandFinished(TerminalCommandFinishedEventImpl(outputModel, block))
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