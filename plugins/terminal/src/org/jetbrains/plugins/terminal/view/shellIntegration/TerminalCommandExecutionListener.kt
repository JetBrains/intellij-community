// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

@ApiStatus.Experimental
interface TerminalCommandExecutionListener {
  /**
   * Called when the "Enter" key is received by the shell, the shell identified
   * the typed command as a complete command and going to execute it.
   */
  fun commandStarted(event: TerminalCommandStartedEvent) {}

  /**
   * Called when shell finished executing the command.
   * The output and other metadata can be accessed from the [TerminalCommandFinishedEvent.commandBlock].
   */
  fun commandFinished(event: TerminalCommandFinishedEvent) {}
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandExecutionEvent {
  /**
   * The same as the [regular][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.regular] output model.
   */
  val outputModel: TerminalOutputModel
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandStartedEvent : TerminalCommandExecutionEvent {
  /** The block with the information about the executing command */
  val commandBlock: TerminalCommandBlock
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandFinishedEvent : TerminalCommandExecutionEvent {
  /** The block with the information about the finished command */
  val commandBlock: TerminalCommandBlock
}