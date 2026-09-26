// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// The text cursor: where it is, and the shape the program asked for. Part of the backend-agnostic API;
// see TerminalEmulator.kt.

/** The text cursor. Coordinates are 0-based within the active screen. */
@ApiStatus.Internal
data class Cursor(val column: Int, val row: Int, val visible: Boolean)

/**
 * Cursor drawing shape, chosen by the program via DECSCUSR (`CSI Ps SP q`): `1`/`2` → [BLOCK],
 * `3`/`4` → [UNDERLINE], `5`/`6` → [BAR]; `0` resets to the configured default. The hollow-block
 * variant some terminals draw for an unfocused window is intentionally absent: it is a render-time
 * decision for the embedder, not a shape a program can select, and this backend never reports it.
 */
@ApiStatus.Internal
enum class CursorShape { BAR, BLOCK, UNDERLINE }
