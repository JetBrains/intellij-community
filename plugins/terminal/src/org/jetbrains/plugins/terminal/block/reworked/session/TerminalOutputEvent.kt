// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.session.StyleRange

@ApiStatus.Internal
sealed interface TerminalOutputEvent

@ApiStatus.Internal
data class TerminalContentUpdatedEvent(
  val text: String,
  val styles: List<StyleRange>,
  val startLineLogicalIndex: Int,
) : TerminalOutputEvent

@ApiStatus.Internal
data class TerminalCursorPositionChangedEvent(
  val logicalLineIndex: Int,
  val columnIndex: Int,
) : TerminalOutputEvent

@ApiStatus.Internal
data class TerminalStateChangedEvent(val state: TerminalStateDto) : TerminalOutputEvent

@ApiStatus.Internal
object TerminalBeepEvent : TerminalOutputEvent

// Shell Integration Events

@ApiStatus.Internal
sealed interface TerminalShellIntegrationEvent : TerminalOutputEvent

@ApiStatus.Internal
object TerminalShellIntegrationInitializedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
data class TerminalCommandStartedEvent(val command: String) : TerminalShellIntegrationEvent

@ApiStatus.Internal
data class TerminalCommandFinishedEvent(val command: String, val exitCode: Int) : TerminalShellIntegrationEvent

@ApiStatus.Internal
object TerminalPromptStartedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
object TerminalPromptFinishedEvent : TerminalShellIntegrationEvent