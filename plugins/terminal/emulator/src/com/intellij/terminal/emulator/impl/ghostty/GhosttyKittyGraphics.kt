// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_LONG
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * Turns the Kitty graphics protocol (`APC G`) off.
 *
 * The library enables the protocol by default, but the IDE draws no images yet.
 * With a zero image storage limit it ignores every protocol command, including
 * the support query, so a program that probes for the protocol gets no reply.
 */
internal object GhosttyKittyGraphics {
  /**
   * Sets the image storage limit of [terminal] to zero.
   *
   * @throws IllegalStateException if the library rejects the option.
   * @throws RuntimeException if the native call fails.
   */
  fun disable(terminal: MemorySegment) {
    val result = try {
      Arena.ofConfined().use { arena ->
        val limit = arena.allocate(C_LONG) // uint64_t*; the library reads the value during the call and keeps no pointer
        limit.set(C_LONG, 0L, 0L)
        LibGhosttyVt.terminalSet(terminal, GhosttyTerminalOption.KITTY_IMAGE_STORAGE_LIMIT.code, limit)
      }
    }
    catch (t: Throwable) {
      rethrowControlFlowException(t)
      throw RuntimeException("disabling the Kitty graphics protocol failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("ghostty_terminal_set(KITTY_IMAGE_STORAGE_LIMIT) returned $result")
    }
  }
}
