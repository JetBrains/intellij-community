package com.intellij.python.terminal.frontend

import com.intellij.openapi.project.Project
import com.intellij.python.terminal.shared.getCurrentVenvPath
import org.jetbrains.plugins.terminal.startup.TerminalWorkingDirectoryCustomizer
import java.nio.file.Path

internal class PyTerminalWorkingDirectoryCustomizer : TerminalWorkingDirectoryCustomizer {
  override suspend fun getContextualStartWorkingDirectory(project: Project): Path? {
    return getCurrentVenvPath(project)
  }
}
