// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt.renderer

import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState

internal class PlaceholderPromptRenderer(
  private val isSingleLine: Boolean,
) : TerminalPromptRenderer {
  override fun calculateRenderingInfo(state: TerminalPromptState): TerminalPromptRenderingInfo {
    return TerminalPromptRenderingInfo(if (isSingleLine) "" else "\n", emptyList())
  }
}
