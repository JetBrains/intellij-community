// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.jediterm.core.util.TermSize

internal sealed interface TerminalInputEvent

internal data class TerminalResizeEvent(val newSize: TermSize) : TerminalInputEvent

internal data class TerminalWriteStringEvent(val string: String) : TerminalInputEvent

internal class TerminalCloseEvent : TerminalInputEvent