// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The terminal colors of the global color scheme, which the session uses as its default colors.
 * A program queries them: [foreground] with `OSC 10 ; ?`, [background] with `OSC 11 ; ?`,
 * [isDark] with `CSI ? 996 n`, and [ansiColors] with `OSC 4 ; n ; ?`.
 * A program can override [foreground], [background], and [ansiColors] with `OSC 10`, `OSC 11`, and `OSC 4`.
 */
@ApiStatus.Internal
@Serializable
data class TerminalColorSchemeDto(
  val foreground: TerminalRgbColorDto,
  val background: TerminalRgbColorDto,
  /** Whether [background] is dark. */
  val isDark: Boolean,
  /** The foreground colors of the ANSI palette slots `0..15`, in slot order. */
  val ansiColors: List<TerminalRgbColorDto>,
)
