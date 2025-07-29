package com.intellij.terminal.backend.util

import com.google.common.base.Ascii
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.terminal.backend.TerminalSessionsManager
import com.intellij.terminal.backend.createTerminalSession
import com.intellij.terminal.backend.startTerminalProcess
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.createShellCommand
import com.intellij.terminal.session.TerminalOutputEvent
import com.intellij.terminal.session.TerminalSession
import com.intellij.util.EnvironmentUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import java.nio.file.Files
import java.nio.file.Path

internal object TerminalSessionTestUtil {
  /** Starts the same terminal session used in production */
  suspend fun startTestTerminalSession(
    project: Project,
    shellPath: String,
    coroutineScope: CoroutineScope,
  ): TerminalSession {
    val shellCommand = createShellCommand(shellPath)
    val options = ShellStartupOptions.Builder().shellCommand(shellCommand).build()
    return startTestTerminalSession(project, options, isLowLevelSession = false, coroutineScope)
  }

  /**
   * @param options should already contain configured [ShellStartupOptions.shellCommand].
   * Use [createShellCommand] to create the default command from the shell path.
   *
   * @param isLowLevelSession whether the same session should be used as in the production or its low-level JediTerm implementation.
   * Low-level session outputs the events in their natural order,
   * while production one replaces some initial events with [com.intellij.terminal.session.TerminalInitialStateEvent].
   * Prefer low-level session in exceptional cases only when you need to test the exact sequences of events.
   * Use production session (specify `false`) in all other cases.
   */
  fun startTestTerminalSession(
    project: Project,
    options: ShellStartupOptions,
    isLowLevelSession: Boolean,
    coroutineScope: CoroutineScope,
  ): TerminalSession {
    assert(options.shellCommand != null) { "shellCommand should be configured in the provided options" }

    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.REWORKED, coroutineScope.asDisposable())

    val allOptions = options.builder()
      .envVariables(options.envVariables + mapOf(EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true", "HISTFILE" to "/dev/null"))
      .workingDirectory(options.workingDirectory ?: System.getProperty("user.home"))
      .initialTermSize(options.initialTermSize ?: TermSize(80, 24))
      .build()

    return if (isLowLevelSession) {
      val (ttyConnector, configuredOptions) = startTerminalProcess(project, allOptions)
      createTerminalSession(project, ttyConnector, configuredOptions, JBTerminalSystemSettingsProvider(), coroutineScope)
    }
    else {
      val manager = TerminalSessionsManager.getInstance()
      val sessionStartResult = manager.startSession(allOptions, project, coroutineScope)
      manager.getSession(sessionStartResult.sessionId)!!
    }
  }

  fun createShellCommand(shellPath: String): List<String> {
    return LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath)
  }

  suspend fun TerminalSession.awaitOutputEvent(targetEvent: TerminalOutputEvent) {
    return coroutineScope {
      val promptFinishedEventDeferred = CompletableDeferred<Unit>()

      val flowCollectionJob = launch {
        getOutputFlow().collect { events ->
          if (events.any { it == targetEvent }) {
            promptFinishedEventDeferred.complete(Unit)
          }
        }
      }

      promptFinishedEventDeferred.await()
      flowCollectionJob.cancel()
    }
  }

  fun getShellPaths(): List<Path> {
    return listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/opt/homebrew/bin/bash",
      "powershell.exe",
      "pwsh.exe"
    ).mapNotNull {
      val path = Path.of(it)
      if (Files.isRegularFile(path)) path else PathEnvironmentVariableUtil.findInPath(it)?.toPath()
    }
  }

  val ENTER_BYTES: ByteArray = byteArrayOf(Ascii.CR)
}