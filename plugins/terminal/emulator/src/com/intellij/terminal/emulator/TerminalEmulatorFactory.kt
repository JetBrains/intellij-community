// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import com.intellij.terminal.emulator.impl.ghostty.GhosttyTerminalEmulator
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a [TerminalEmulator] backed by the bundled Ghostty VT engine.
 *
 * Kept out of `TerminalEmulator.kt` so the API file itself has no dependency on a concrete engine.
 *
 * @param initialSize the initial screen size; the emulator can be resized later via [TerminalEmulator.resize].
 * @param maxScrollbackBytes maximum scrollback size in bytes; must be >= 0 (defaults to 1 MB).
 *   `0` disables scrollback entirely (scrolled-off lines are dropped immediately). Otherwise the value
 *   is quantized to whole 400 KiB storage pages with a two-page (800 KiB) minimum: anything from 1 byte
 *   up to ~1.17 MB keeps that 800 KiB, and it then grows in 400 KiB steps. How many rows that holds
 *   depends on the terminal width, since storage is charged per grid cell (~9 bytes each, including
 *   blank cells), not per line.
 */
@ApiStatus.Internal
fun createTerminalEmulator(
  initialSize: TerminalSize,
  maxScrollbackBytes: Int = 1024 * 1024,
): TerminalEmulator {
  require(maxScrollbackBytes >= 0) { "maxScrollbackBytes must be non-negative, was $maxScrollbackBytes" }
  return GhosttyTerminalEmulator(initialSize, maxScrollbackBytes.toLong())
}
