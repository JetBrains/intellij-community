// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

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