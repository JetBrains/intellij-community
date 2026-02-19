package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import org.jetbrains.plugins.terminal.DetectedShellInfo
import org.jetbrains.plugins.terminal.TerminalShellsDetector
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalShellsDetectorApi

internal class TerminalShellsDetectorApiImpl : TerminalShellsDetectorApi {
  override suspend fun detectShells(projectId: ProjectId): List<DetectedShellInfo> {
    return TerminalShellsDetector.detectShells(projectId.findProject())
  }
}