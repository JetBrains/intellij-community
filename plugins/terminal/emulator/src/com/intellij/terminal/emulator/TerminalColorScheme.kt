// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// The dark or light color scheme a program asks for with CSI ? 996 n. Part of the backend-agnostic API; see
// TerminalEmulator.kt.

/** Whether the embedder colors are dark or light. See [TerminalEmulator.setColorScheme]. */
@ApiStatus.Internal
enum class ColorScheme {
  LIGHT,
  DARK,
}
