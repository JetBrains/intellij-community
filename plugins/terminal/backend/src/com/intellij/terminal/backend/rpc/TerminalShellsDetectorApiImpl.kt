package com.intellij.terminal.backend.rpc

import org.jetbrains.plugins.terminal.DetectedShellInfo
import org.jetbrains.plugins.terminal.TerminalShellsDetector
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalShellsDetectorApi

internal class TerminalShellsDetectorApiImpl : TerminalShellsDetectorApi {
  override suspend fun detectShells(): List<DetectedShellInfo> {
    return TerminalShellsDetector.detectShells()
  }
}