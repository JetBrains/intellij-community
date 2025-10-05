// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface TerminalDisplayListener : EventListener {
  fun cursorPositionChanged(x: Int, y: Int) {}

  fun cursorVisibilityChanged(isVisible: Boolean) {}

  fun cursorShapeChanged(cursorShape: CursorShape?) {}

  fun mouseModeChanged(mode: MouseMode) {}

  fun mouseFormatChanged(format: MouseFormat) {}

  fun bracketedPasteModeChanged(isEnabled: Boolean) {}

  fun windowTitleChanged(title: String) {}

  fun beep() {}
}