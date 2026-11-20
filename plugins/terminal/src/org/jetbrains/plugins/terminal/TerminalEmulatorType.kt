// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
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
  /** JediTerm, driven through `com.jediterm.terminal.emulator.JediEmulator`. */
  JediTerm,

  /** Ghostty (`libghostty-vt`), driven through the `TerminalEmulator` API. */
  Ghostty,

  /** KetraTerm's session pipeline and Swing renderer. */
  KetraTerm,
  ;

  companion object {
    /**
     * Selects the configured backend. An empty override preserves the existing Ghostty registry setting.
     */
    val default: TerminalEmulatorType
      get() {
        val selected = RegistryManager.getInstance().get("terminal.emulator").asString()
        return entries.firstOrNull { it.name.equals(selected, ignoreCase = true) }
               ?: if (Registry.`is`("terminal.use.ghostty.emulator")) Ghostty else JediTerm
      }
  }
}
