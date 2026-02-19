package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import org.jetbrains.plugins.terminal.shellDetection.DetectedShellInfo
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionApi
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionService

internal class TerminalShellsDetectionApiImpl : TerminalShellsDetectionApi {
  override suspend fun detectShells(projectId: ProjectId): List<DetectedShellInfo> {
    return TerminalShellsDetectionService.detectShells(projectId.findProject())
  }
}