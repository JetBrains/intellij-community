// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import java.util.*

internal interface TerminalShellIntegrationEventsListener : EventListener {
  fun initialized() {}

  fun commandStarted(command: String) {}

  fun commandFinished(command: String, exitCode: Int) {}

  fun promptStarted() {}

  fun promptFinished() {}
}