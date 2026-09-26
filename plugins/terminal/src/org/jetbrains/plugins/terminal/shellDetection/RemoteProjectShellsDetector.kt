// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.productMode.IdeProductMode
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.LOCAL_ENVIRONMENT_NAME
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectUnixShells
import org.jetbrains.plugins.terminal.shellDetection.TerminalShellsDetectionUtil.detectWindowsShells

/**
 * Provides shells for the RemDev case.
 * Detects available shells in the remote environment where the project is located.
 */
internal class RemoteProjectShellsDetector : TerminalShellsDetector {
  override suspend fun detectShells(project: Project): ShellsDetectionResult {
    check(isApplicable(project)) { "Should only be called if isApplicable() == true" }

    val eelDescriptor = project.getEelDescriptor()
    if (eelDescriptor == LocalEelDescriptor) {
      thisLogger().warn("Local EelDescriptor found for remote project, skipping shells detection.")
      return ShellsDetectionResult(emptyList())
    }

    val shells = when (eelDescriptor.osFamily) {
      EelOsFamily.Posix -> detectUnixShells(eelDescriptor)
      EelOsFamily.Windows -> detectWindowsShells(eelDescriptor)
    }

    // Use the same "Host" name for remote env as for the local one. Anyway, it won't be shown because there is a single env.
    val envInfo = DetectedShellsEnvironmentInfo(LOCAL_ENVIRONMENT_NAME, shells)
    return ShellsDetectionResult(listOf(envInfo))
  }

  override fun isApplicable(project: Project): Boolean {
    return IdeProductMode.isFrontend
  }
}