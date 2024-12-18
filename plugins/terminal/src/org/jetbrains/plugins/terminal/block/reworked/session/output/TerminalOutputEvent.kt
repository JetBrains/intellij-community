// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import org.jetbrains.plugins.terminal.block.session.StyleRange

internal sealed interface TerminalOutputEvent

internal data class TerminalContentUpdatedEvent(
  val text: String,
  val styles: List<StyleRange>,
  val startLineLogicalIndex: Int,
) : TerminalOutputEvent

internal data class TerminalCursorPositionChangedEvent(
  val logicalLineIndex: Int,
  val columnIndex: Int,
) : TerminalOutputEvent

internal data class TerminalStateChangedEvent(val state: TerminalStateDto) : TerminalOutputEvent

internal object TerminalBeepEvent : TerminalOutputEvent

// Shell Integration Events

internal sealed interface TerminalShellIntegrationEvent : TerminalOutputEvent

internal object TerminalShellIntegrationInitializedEvent : TerminalShellIntegrationEvent

internal data class TerminalCommandStartedEvent(val command: String) : TerminalShellIntegrationEvent

internal data class TerminalCommandFinishedEvent(val command: String, val exitCode: Int) : TerminalShellIntegrationEvent

internal object TerminalPromptStartedEvent : TerminalShellIntegrationEvent

internal object TerminalPromptFinishedEvent : TerminalShellIntegrationEvent