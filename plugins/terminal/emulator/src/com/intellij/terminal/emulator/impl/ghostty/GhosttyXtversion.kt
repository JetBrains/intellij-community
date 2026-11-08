// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_PTR
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.STRING
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.allocateString
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyTerminalOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles

/**
 * Answers the XTVERSION query (`CSI > q`) with [NAME].
 *
 * Without the callback the library reports `libghostty`. A programs that identifies
 * Ghostty by the "ghostty" substring may skip proper capability checks.
 * For example, `yazi` then sends graphics even if Kitty graphics protocol is disabled.
 * The IDE terminal reports its own name, so programs probe the capabilities instead.
 */
internal class GhosttyXtversion {
  // The `GhosttyString` that the callback returns by value. The library copies the bytes after
  // the upcall returns, within the same write call, so they live in the arena of [install].
  private lateinit var nameString: MemorySegment

  /**
   * Installs the XTVERSION callback on [terminal]. The name bytes and the upcall stub live in [arena].
   *
   * @throws IllegalStateException if the library rejects the callback.
   * @throws RuntimeException if the stub cannot be created or the native call fails.
   */
  fun install(terminal: MemorySegment, arena: Arena) {
    val result = try {
      nameString = allocateString(arena, NAME)
      val handle = MethodHandles.lookup().bind(this, "onQuery", XTVERSION_DESC.toMethodType())
      val stub = Linker.nativeLinker().upcallStub(handle, XTVERSION_DESC, arena)
      LibGhosttyVt.terminalSet(terminal, GhosttyTerminalOption.XTVERSION.code, stub)
    }
    catch (t: Throwable) {
      rethrowControlFlowException(t)
      throw RuntimeException("installing XTVERSION callback failed", t)
    }
    if (result != GhosttyResult.SUCCESS) {
      throw IllegalStateException("ghostty_terminal_set(XTVERSION) returned $result")
    }
  }

  /**
   * Invoked from native code when the program sends `CSI > q`. Returns [nameString] by value.
   *
   * Never throws: an exception thrown out of an FFM upcall terminates the JVM.
   */
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onQuery(terminal: MemorySegment, userdata: MemorySegment): MemorySegment = nameString

  companion object {
    /**
     * The terminal name in the XTVERSION reply `DCS > | name ST`.
     *
     * It must not contain `ghostty`. A program that finds that substring may assume
     * that the terminal is Ghostty and has the same capabilities, such as image support.
     * It must be at most 256 bytes, or the library sends no reply.
     * An empty name falls back to `libghostty`.
     */
    const val NAME: String = "JetBrains Terminal"

    // `GhosttyTerminalXtversionFn` (terminal.h): `GhosttyString(terminal, userdata)`.
    private val XTVERSION_DESC: FunctionDescriptor = FunctionDescriptor.of(STRING, C_PTR, C_PTR)
  }
}
