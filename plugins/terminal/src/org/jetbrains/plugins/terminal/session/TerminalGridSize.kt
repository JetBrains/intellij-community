// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Terminal content is rendered in a grid of characters.
 * This class represents the size of the grid.
 */
@ApiStatus.Experimental
@Serializable
data class TerminalGridSize(
  val columns: Int,
  val rows: Int,
) {
  init {
    check(columns >= 0) { "columns must be positive" }
    check(rows >= 0) { "rows must be positive" }
  }
}