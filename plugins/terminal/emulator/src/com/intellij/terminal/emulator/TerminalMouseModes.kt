// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Mouse-reporting modes the UI must honor when encoding input for the program. Part of the
// backend-agnostic API; see TerminalEmulator.kt.

/** Mouse reporting the running program requested (which events to report). */
@ApiStatus.Internal
enum class MouseProtocol { NONE, X10, NORMAL, BUTTON, ANY }

/** Encoding of the mouse reports sent back to the program (how they are framed). */
@ApiStatus.Internal
enum class MouseEncoding { DEFAULT, UTF8, SGR, URXVT, SGR_PIXELS }
