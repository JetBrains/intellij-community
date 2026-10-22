// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalGridSize

/**
 * The grid size with the pixel size of one of its cells.
 * See `com.intellij.terminal.emulator.TerminalGridGeometry`.
 */
@ApiStatus.Internal
@Serializable
data class TerminalGridGeometryDto(
  val gridSize: TerminalGridSize,
  val cellSize: CellPixelSizeDto,
)
