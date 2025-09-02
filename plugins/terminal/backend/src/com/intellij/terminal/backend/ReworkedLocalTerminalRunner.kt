package com.intellij.terminal.backend

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner

/**
 * Terminal Runner that starts the local terminal with Reworked (Gen2) shell integration.
 */
internal class ReworkedLocalTerminalRunner(project: Project) : LocalTerminalDirectRunner(project) {
  override fun isGenTwoTerminalEnabled(): Boolean {
    return true
  }
}