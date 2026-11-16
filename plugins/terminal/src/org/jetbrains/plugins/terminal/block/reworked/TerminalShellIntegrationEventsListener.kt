// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.platform.util.annotations.paths.OsPath
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

// TODO: use EelPath instead of String for the paths, so that a path keeps the Eel environment of the process.
@ApiStatus.Internal
interface TerminalShellIntegrationEventsListener : EventListener {
  fun initialized(currentDirectory: @OsPath String?, historyPath: @OsPath String?) {}

  fun commandStarted(command: String) {}

  fun commandFinished(command: String, exitCode: Int, currentDirectory: @OsPath String?) {}

  fun promptStarted() {}

  fun promptFinished() {}

  fun aliasesReceived(aliasesRaw: String) {}

  fun completionFinished(result: String) {}
}