// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo

@ApiStatus.Internal
data class TerminalPromptRenderingInfo(
  val text: @NlsSafe String,
  val highlightings: List<HighlightingInfo>,
  val rightText: @NlsSafe String = "",
  val rightHighlightings: List<HighlightingInfo> = emptyList()
)
