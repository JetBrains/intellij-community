package com.intellij.terminal.backend.util

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.backend.startTerminalSession
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.EnvironmentUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import java.nio.file.Files
import java.nio.file.Path

internal object TerminalSessionTestUtil {
  fun startTestTerminalSession(
    shellPath: String,
    project: Project,
    coroutineScope: CoroutineScope,
    size: TermSize = TermSize(80, 24),
  ): TerminalSession {
    Registry.Companion.get(LocalBlockTerminalRunner.Companion.BLOCK_TERMINAL_REGISTRY).setValue(true, coroutineScope.asDisposable())
    Registry.Companion.get(LocalBlockTerminalRunner.Companion.REWORKED_BLOCK_TERMINAL_REGISTRY).setValue(true, coroutineScope.asDisposable())

    val options = ShellStartupOptions.Builder()
      .shellCommand(listOf(shellPath))
      .initialTermSize(size)
      .envVariables(mapOf(EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true", "HISTFILE" to "/dev/null"))
      .build()
    return startTerminalSession(project, options, JBTerminalSystemSettingsProviderBase(), coroutineScope)
  }

  fun getShellPaths(): List<Path> {
    return listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/opt/homebrew/bin/bash",
    ).mapNotNull {
      val path = Path.of(it)
      if (Files.isRegularFile(path)) path else PathEnvironmentVariableUtil.findInPath(it)?.toPath()
    }
  }
}