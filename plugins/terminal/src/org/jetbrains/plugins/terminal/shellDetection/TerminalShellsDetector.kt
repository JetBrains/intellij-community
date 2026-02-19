// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Should be used to provide shells for [TerminalShellsDetectionService] for different environments where a project can be opened.
 * Examples of the supported environments:
 * 1. Local - project is opened in the same file system where IDE is running.
 * 2. WSL - project is opened in WSL environment, while the IDE is running on Windows.
 * 3. Docker - project is opened in a Docker container, while the IDE is running on the host machine.
 *
 * To provide shells for any of these environments, implement this interface and register it as an extension.
 */
@ApiStatus.Internal
interface TerminalShellsDetector {
  suspend fun detectShells(project: Project): List<DetectedShellInfo>

  fun isApplicable(project: Project): Boolean

  companion object {
    internal val EP_NAME = ExtensionPointName<TerminalShellsDetector>("org.jetbrains.plugins.terminal.shellsDetector")
  }
}