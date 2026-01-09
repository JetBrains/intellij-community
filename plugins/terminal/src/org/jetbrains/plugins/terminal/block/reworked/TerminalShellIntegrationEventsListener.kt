// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.TerminalAliasesInfo
import java.util.*

@ApiStatus.Internal
interface TerminalShellIntegrationEventsListener : EventListener {
  fun initialized(currentDirectory: String) {}

  fun commandStarted(command: String) {}

  fun commandFinished(command: String, exitCode: Int, currentDirectory: String) {}

  fun promptStarted() {}

  fun promptFinished() {}

  fun aliasesReceived(aliases: TerminalAliasesInfo) {}

  fun completionFinished(result: String) {}
}