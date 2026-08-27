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
 * @param maxScrollbackBytes see [GhosttyTerminalEmulator] docs.
 */
@ApiStatus.Internal
fun createTerminalEmulator(
  initialSize: TerminalSize,
  maxScrollbackBytes: Int = 10 * 1024 * 1024,
): TerminalEmulator {
  require(maxScrollbackBytes >= 0) { "maxScrollbackBytes must be non-negative, was $maxScrollbackBytes" }
  return GhosttyTerminalEmulator(initialSize, maxScrollbackBytes.toLong())
}
