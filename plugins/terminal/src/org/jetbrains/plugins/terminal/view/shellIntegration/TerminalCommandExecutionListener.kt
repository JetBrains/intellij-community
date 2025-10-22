// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.util.*

@ApiStatus.Experimental
interface TerminalCommandExecutionListener : EventListener {
  fun commandStarted(event: TerminalCommandStartedEvent) {}

  fun commandFinished(event: TerminalCommandFinishedEvent) {}
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandExecutionEvent {
  val outputModel: TerminalOutputModel
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandStartedEvent : TerminalCommandExecutionEvent {
  val commandBlock: TerminalCommandBlock
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandFinishedEvent : TerminalCommandExecutionEvent {
  val commandBlock: TerminalCommandBlock
}