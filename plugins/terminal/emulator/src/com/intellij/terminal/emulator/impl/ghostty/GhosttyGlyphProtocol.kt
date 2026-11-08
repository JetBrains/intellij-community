// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_BOOL
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * Turns the Glyph protocol (`APC 25a1 ; ...`) off.
 *
 * The library enables the protocol by default, but the IDE draws every cell with fonts and
 * never uses a registered outline. With the protocol off the library ignores every protocol
 * command, including the support query, so a program that probes for the protocol gets no
 * reply and keeps its font glyphs.
 */
internal object GhosttyGlyphProtocol {
  /**
   * Sets the Glyph protocol option of [terminal] to false.
   *
   * @throws IllegalStateException if the library rejects the option.
   * @throws RuntimeException if the native call fails.
   */
  fun disable(terminal: MemorySegment) {
    val result = try {
      Arena.ofConfined().use { arena ->
        val enabled = arena.allocate(C_BOOL) // bool*; the library reads the value during the call and keeps no pointer
        enabled.set(C_BOOL, 0L, false)
        LibGhosttyVt.terminalSet(terminal, GhosttyTerminalOption.GLYPH_PROTOCOL.code, enabled)
      }
    }
    catch (t: Throwable) {
      rethrowControlFlowException(t)
      throw RuntimeException("disabling the Glyph protocol failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("ghostty_terminal_set(GLYPH_PROTOCOL) returned $result")
    }
  }
}
