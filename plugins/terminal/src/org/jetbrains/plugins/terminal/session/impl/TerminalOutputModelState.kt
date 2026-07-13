// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalOutputModelState(
  val text: String,
  val trimmedLinesCount: Long,
  val trimmedCharsCount: Long,
  val firstLineTrimmedCharsCount: Int,
  val cursorOffset: Int,
  val highlightings: List<StyleRange>,
  /** [Osc8Hyperlink] with absolute offsets. */
  val osc8Hyperlinks: List<Osc8Hyperlink>,
)