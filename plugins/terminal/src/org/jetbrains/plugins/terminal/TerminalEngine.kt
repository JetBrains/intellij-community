// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
enum class TerminalEngine(val presentableName: @Nls String) {
  REWORKED(TerminalBundle.message("terminal.engine.reworked")),
  CLASSIC(TerminalBundle.message("terminal.engine.classic")),
  NEW_TERMINAL(TerminalBundle.message("terminal.engine.new.terminal"));

  companion object {
    fun getValue(): TerminalEngine {
      val isReworkedRegistryEnabled = Registry.`is`(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY)
      val isNewTerminalRegistryEnabled = Registry.`is`(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY)

      // Order of conditions is important!
      // New Terminal registry prevails, even if reworked registry is enabled.
      return when {
        isNewTerminalRegistryEnabled -> NEW_TERMINAL
        isReworkedRegistryEnabled -> REWORKED
        else -> CLASSIC
      }
    }

    fun setValue(engine: TerminalEngine) {
      when (engine) {
        REWORKED -> {
          Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(false)
          Registry.get(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY).setValue(true)
        }
        CLASSIC -> {
          Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(false)
          Registry.get(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY).setValue(false)
        }
        NEW_TERMINAL -> {
          Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(true)
          Registry.get(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY).setValue(false)
        }
      }
    }
  }
}