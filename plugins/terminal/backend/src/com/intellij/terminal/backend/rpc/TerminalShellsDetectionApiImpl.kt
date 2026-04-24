package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import org.jetbrains.plugins.terminal.shellDetection.ShellsDetectionResult
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionApi
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionService

internal class TerminalShellsDetectionApiImpl : TerminalShellsDetectionApi {
  override suspend fun detectShells(projectId: ProjectId): ShellsDetectionResult {
    return TerminalShellsDetectionService.detectShells(projectId.findProject())
  }
}