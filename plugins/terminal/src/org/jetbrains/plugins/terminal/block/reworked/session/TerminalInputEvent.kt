// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.jediterm.core.util.TermSize
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface TerminalInputEvent

@ApiStatus.Internal
data class TerminalResizeEvent(val newSize: TermSize) : TerminalInputEvent

@ApiStatus.Internal
data class TerminalWriteBytesEvent(val bytes: ByteArray) : TerminalInputEvent {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalWriteBytesEvent

    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }
}

@ApiStatus.Internal
class TerminalCloseEvent : TerminalInputEvent

@ApiStatus.Internal
object TerminalClearBufferEvent : TerminalInputEvent