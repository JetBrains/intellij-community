// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.LOCAL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.WSL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectUnixShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWindowsShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWslDistributions

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
