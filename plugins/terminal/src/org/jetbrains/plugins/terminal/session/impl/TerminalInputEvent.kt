// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorSchemeDto
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
@Serializable
sealed interface TerminalInputEvent {
  val id: Int
}

@ApiStatus.Internal
@Serializable
sealed class TerminalInputEventBase : TerminalInputEvent {
  override val id: Int = inputEventIdCounter.getAndIncrement()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalInputEventBase

    return id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  override fun toString(): String {
    return "${javaClass.simpleName}(id=$id)"
  }
}

@ApiStatus.Internal
@Serializable
data class TerminalResizeEvent(val newSize: TerminalGridSize) : TerminalInputEventBase()

@ApiStatus.Internal
@Serializable
data class TerminalWriteBytesEvent(val bytes: ByteArray) : TerminalInputEventBase() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalWriteBytesEvent

    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun toString(): String {
    return "TerminalWriteBytesEvent(bytes=${bytes.contentToString()})"
  }
}

@ApiStatus.Internal
@Serializable
class TerminalCloseEvent : TerminalInputEventBase()

@ApiStatus.Internal
@Serializable
class TerminalClearBufferEvent : TerminalInputEventBase()

/** Sets the default colors of the session. See [TerminalColorSchemeDto]. */
@ApiStatus.Internal
@Serializable
data class TerminalSetColorSchemeEvent(val colorScheme: TerminalColorSchemeDto) : TerminalInputEventBase()

/** Sets the default cursor shape. A program can override it with DECSCUSR (`CSI Ps SP q`). */
@ApiStatus.Internal
@Serializable
data class TerminalSetDefaultCursorShapeEvent(val cursorShape: CursorShapeDto) : TerminalInputEventBase()

private val inputEventIdCounter = AtomicInteger(0)