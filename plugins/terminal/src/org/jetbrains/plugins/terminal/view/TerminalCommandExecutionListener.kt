// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandBlock
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import java.util.*

@ApiStatus.Internal
interface TerminalCommandExecutionListener : EventListener {
  fun commandStarted(event: TerminalCommandStartedEvent) {}

  fun commandFinished(event: TerminalCommandFinishedEvent) {}
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface TerminalCommandExecutionEvent {
  val outputModel: TerminalOutputModel
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface TerminalCommandStartedEvent : TerminalCommandExecutionEvent {
  val commandBlock: TerminalCommandBlock
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface TerminalCommandFinishedEvent : TerminalCommandExecutionEvent {
  val commandBlock: TerminalCommandBlock
}