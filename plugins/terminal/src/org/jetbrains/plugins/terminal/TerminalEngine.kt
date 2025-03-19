// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
enum class TerminalEngine(val presentableName: @Nls String) {
  REWORKED(TerminalBundle.message("terminal.engine.reworked")),
  CLASSIC(TerminalBundle.message("terminal.engine.classic")),
  NEW_TERMINAL(TerminalBundle.message("terminal.engine.new.terminal"));
}