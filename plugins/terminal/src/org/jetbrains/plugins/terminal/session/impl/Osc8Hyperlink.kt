// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import org.jetbrains.annotations.ApiStatus

/**
 * OSC8 terminal hyperlink detected in the terminal output: the [uri] target attached to the text
 * range `[startOffset, endOffset)`.
 * See https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda
 *
 * Offsets are relative when produced by the scraper; absolute when stored in the model state.
 */
@ApiStatus.Internal
data class Osc8Hyperlink(
  val startOffset: Long,
  val endOffset: Long,
  val uri: String,
)
