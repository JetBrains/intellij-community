// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TerminalShellsDetectionService {
  /**
   * Finds available shells that can be started in the embedded terminal in the provided [project].
   */
  suspend fun detectShells(project: Project): List<DetectedShellInfo> {
    val detector = TerminalShellsDetector.EP_NAME.extensionList.firstOrNull { it.isApplicable(project) }
    return if (detector != null) {
      LOG.debug { "Using $detector to detect shells for $project" }
      withContext(Dispatchers.IO) {
        val shells = detector.detectShells(project)
        LOG.debug { "Detected shells:\n${shells.joinToString("\n")}" }
        shells
      }
    }
    else {
      LOG.warn("Didn't find any applicable shell detectors for $project")
      emptyList()
    }
  }

  private val LOG = logger<TerminalShellsDetectionService>()
}