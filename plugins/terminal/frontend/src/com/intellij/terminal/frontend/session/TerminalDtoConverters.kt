// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.terminal.emulator.CellPixelSize
import com.intellij.terminal.emulator.TerminalGridGeometry
import com.intellij.terminal.emulator.TerminalSize
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.dto.CellPixelSizeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalGridGeometryDto

// A converter lives next to its DTO when the DTO module can see the target type.
// If it cannot, the converter lives here.
// For example, the main plugin module cannot depend on `intellij.terminal.emulator`.

internal fun TerminalGridGeometryDto.toTerminalGridGeometry(): TerminalGridGeometry =
  TerminalGridGeometry(gridSize.toTerminalSize(), cellSize.toCellPixelSize())

internal fun TerminalGridGeometry.toDto(): TerminalGridGeometryDto =
  TerminalGridGeometryDto(gridSize.toTerminalGridSize(), cellSize.toDto())

internal fun CellPixelSizeDto.toCellPixelSize(): CellPixelSize = CellPixelSize(width, height)

internal fun CellPixelSize.toDto(): CellPixelSizeDto = CellPixelSizeDto(width, height)

/** @throws IllegalArgumentException if [TerminalSize] rejects the dimensions. */
internal fun TerminalGridSize.toTerminalSize(): TerminalSize = TerminalSize(columns, rows)

internal fun TerminalSize.toTerminalGridSize(): TerminalGridSize = TerminalGridSize(columns, rows)

/** @throws IllegalArgumentException if [TerminalSize] rejects the dimensions. */
internal fun TermSize.toTerminalSize(): TerminalSize = TerminalSize(columns, rows)
