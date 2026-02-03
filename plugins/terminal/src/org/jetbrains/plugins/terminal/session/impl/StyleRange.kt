// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import com.jediterm.terminal.TextStyle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class StyleRange(
  val startOffset: Long,
  val endOffset: Long,
  val style: TextStyle,
  val ignoreContrastAdjustment: Boolean = true,
)