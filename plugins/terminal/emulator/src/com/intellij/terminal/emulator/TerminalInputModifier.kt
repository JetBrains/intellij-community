// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

/** Keyboard modifier state at the time of a [TerminalKeyEvent] or [TerminalMouseEvent]. */
@ApiStatus.Internal
enum class TerminalInputModifier {
  SHIFT,
  CTRL,
  ALT,
  SUPER,
  CAPS_LOCK,
  NUM_LOCK,
}
