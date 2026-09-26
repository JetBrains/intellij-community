// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.LOCAL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectUnixShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWindowsShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWslDistributions

internal class LocalProjectShellsDetector : TerminalShellsDetector {
  override suspend fun detectShells(project: Project): ShellsDetectionResult {
    check(isApplicable(project)) { "Should only be called if isApplicable() == true" }

    val eelDescriptor = project.getEelDescriptor()
    val shells = when (eelDescriptor.osFamily) {
      EelOsFamily.Posix -> detectUnixShells(eelDescriptor)
      EelOsFamily.Windows -> detectWindowsShells(eelDescriptor) + detectWslDistributions()
    }

    val envInfo = DetectedShellsEnvironmentInfo(LOCAL_ENVIRONMENT_NAME, shells)
    return ShellsDetectionResult(listOf(envInfo))
  }

  override fun isApplicable(project: Project): Boolean {
    return project.getEelDescriptor() == LocalEelDescriptor
  }
}
