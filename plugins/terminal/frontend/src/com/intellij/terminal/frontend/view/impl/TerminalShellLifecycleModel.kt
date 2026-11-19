// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.frontend.view.TerminalShellLifecycle
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.ExecutingCommand
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.TypingCommand
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus.WaitingForPrompt

internal class TerminalShellLifecycleModel : TerminalShellLifecycle, TerminalOutputEventsHandler {
  private val status = MutableStateFlow<TerminalOutputStatus?>(null)
  override val outputStatus = status.asStateFlow()
  @Volatile override var runningCommand: String? = null
    private set
  private val listeners = DisposableWrapperList<TerminalShellLifecycle.Listener>()

  override fun addListener(parentDisposable: Disposable, listener: TerminalShellLifecycle.Listener) {
    listeners.add(listener, parentDisposable)
  }

  override suspend fun handleEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalStateChangedEvent -> if (event.state.isShellIntegrationEnabled && status.value == null) {
        status.value = WaitingForPrompt
      }
      is TerminalInitialStateEvent -> {
        val block = event.blocksModelState.toState().blocks.lastOrNull() as? TerminalCommandBlock
        status.value = when {
          !event.sessionState.isShellIntegrationEnabled -> null
          block?.outputStartOffset != null && block.exitCode == null -> ExecutingCommand
          block?.commandStartOffset != null && block.outputStartOffset == null -> TypingCommand
          else -> WaitingForPrompt
        }
        runningCommand = block?.executedCommand.takeIf { status.value == ExecutingCommand }
      }
      TerminalPromptStartedEvent -> {
        runningCommand = null
        status.value = WaitingForPrompt
      }
      TerminalPromptFinishedEvent -> {
        runningCommand = null
        status.value = TypingCommand
      }
      is TerminalCommandStartedEvent -> {
        runningCommand = event.command
        status.value = ExecutingCommand
        fireListenersAndLogAllExceptions(listeners, LOG, { "Shell start listener failed" }) { it.commandStarted(event.command) }
      }
      is TerminalCommandFinishedEvent -> {
        val command = runningCommand
        runningCommand = null
        status.value = WaitingForPrompt
        fireListenersAndLogAllExceptions(listeners, LOG, { "Shell finish listener failed" }) { it.commandFinished(command, event.exitCode) }
      }
      TerminalSessionTerminatedEvent -> {
        runningCommand = null
        status.value = null
      }
      else -> Unit
    }
  }

  companion object {
    private val LOG = logger<TerminalShellLifecycleModel>()
  }
}
