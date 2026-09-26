// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * The terminal emulator driving a single [org.jetbrains.plugins.terminal.session.impl.TerminalSession].
 *
 * Not to be confused with [TerminalEngine], which selects the terminal implementation generation
 * (Reworked / Classic / New Terminal).
 *
 * When unspecified, [default] decides.
 */
@ApiStatus.Internal
enum class TerminalEmulatorType {
  /** JediTerm, driven through its own `com.jediterm.terminal.emulator.JediEmulator`; the default pipeline. */
  JediTerm,

  /** Ghostty (`libghostty-vt`), driven through the `TerminalEmulator` API. */
  Ghostty,
  ;

  companion object {
    /**
     * The emulator used when not specified explicitly: chosen by the `terminal.use.ghostty.emulator`
     * registry key, whose default is `false` — [JediTerm].
     */
    val default: TerminalEmulatorType
      get() = if (Registry.`is`("terminal.use.ghostty.emulator")) Ghostty else JediTerm
  }
}
