package com.intellij.terminal.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import org.jetbrains.plugins.terminal.shellDetection.DetectedShellsEnvironmentInfo
import org.jetbrains.plugins.terminal.shellDetection.ShellsDetectionResult
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.LOCAL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.WSL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectUnixShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWindowsShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWslDistributions
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetector

/**
 * When the project is opened in the WSL file system, it is worth allowing starting shells from both WSL and the host OS.
 * So, it detects Unix shells in the WSL and Windows shells in the host OS.
 */
internal class WslProjectShellsDetector : TerminalShellsDetector {
  override suspend fun detectShells(project: Project): ShellsDetectionResult {
    check(isApplicable(project)) { "Should only be called if isApplicable() == true" }

    val wslEelDescriptor = project.getEelDescriptor()
    val hostEelDescriptor = LocalEelDescriptor

    val wslShells = detectUnixShells(wslEelDescriptor)
    val hostShells = detectWindowsShells(hostEelDescriptor) + detectWslDistributions()

    val envInfo = listOf(
      DetectedShellsEnvironmentInfo(WSL_ENVIRONMENT_NAME, wslShells),
      DetectedShellsEnvironmentInfo(LOCAL_ENVIRONMENT_NAME, hostShells),
    )
    return ShellsDetectionResult(envInfo)
  }

  override fun isApplicable(project: Project): Boolean {
    return project.getEelDescriptor() is WslEelDescriptor
  }
}