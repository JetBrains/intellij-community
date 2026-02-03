package com.intellij.terminal.tests.reworked.util

import com.google.common.base.Ascii
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.terminal.backend.TerminalSessionsManager
import com.intellij.terminal.backend.createTerminalSession
import com.intellij.terminal.backend.startTerminalProcess
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil.createShellCommand
import com.intellij.util.EnvironmentUtil
import com.intellij.util.PathUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.windows.conpty.WinConPtyProcess
import com.pty4j.windows.cygwin.CygwinPtyProcess
import com.pty4j.windows.winpty.WinPtyProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.util.ShellEelProcess
import org.junit.Assert
import org.junit.Assume
import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.nio.file.Path

internal object TerminalSessionTestUtil {
  /** Starts the same terminal session used in production */
  fun startTestTerminalSession(
    project: Project,
    shellPath: String,
    workingDirectory: String?,
    coroutineScope: CoroutineScope,
  ): TestTerminalSessionResult {
    val shellCommand = createShellCommand(shellPath)
    val options = ShellStartupOptions.Builder()
      .shellCommand(shellCommand)
      .workingDirectory(workingDirectory)
      .build()
    return startTestTerminalSession(project, options, isLowLevelSession = false, coroutineScope)
  }

  /**
   * @param options should already contain configured [ShellStartupOptions.shellCommand].
   * Use [createShellCommand] to create the default command from the shell path.
   *
   * @param isLowLevelSession whether the same session should be used as in the production or its low-level JediTerm implementation.
   * Low-level session outputs the events in their natural order,
   * while production one replaces some initial events with [org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent].
   * Prefer low-level session in exceptional cases only when you need to test the exact sequences of events.
   * Use production session (specify `false`) in all other cases.
   */
  fun startTestTerminalSession(
    project: Project,
    options: ShellStartupOptions,
    isLowLevelSession: Boolean,
    coroutineScope: CoroutineScope,
  ): TestTerminalSessionResult {
    assert(options.shellCommand != null) { "shellCommand should be configured in the provided options" }

    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.REWORKED, coroutineScope.asDisposable())

    val allOptions = options.builder()
      .envVariables(options.envVariables + mapOf(EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true", "HISTFILE" to "/dev/null"))
      .initialTermSize(options.initialTermSize ?: TermSize(80, 24))
      .build()

    return if (isLowLevelSession) {
      val (ttyConnector, configuredOptions) = startTerminalProcess(project, allOptions)
      val session = createTerminalSession(project, ttyConnector, configuredOptions, JBTerminalSystemSettingsProvider(), coroutineScope)
      TestTerminalSessionResult(session, ttyConnector)
    }
    else {
      val manager = TerminalSessionsManager.getInstance()
      val sessionStartResult = manager.startSession(allOptions, project, coroutineScope)
      val session = manager.getSession(sessionStartResult.sessionId)!!
      TestTerminalSessionResult(session, sessionStartResult.ttyConnector)
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

  fun assumeCommandBlockShellIntegration(shellCommand: List<String>) {
    assertThat(TerminalOptionsProvider.instance.shellIntegration).isTrue()
    val shellName = PathUtil.getFileName(shellCommand.first())
    Assume.assumeTrue(LocalShellIntegrationInjector.supportsBlocksShellIntegration(shellName, LocalEelDescriptor))
  }

  fun getShellPaths(): List<Path> {
    val traditionalUnixShells = listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/opt/homebrew/bin/bash"
    ).mapNotNull { path ->
      Path.of(path).takeIf { Files.isRegularFile(it) }
    }

    return traditionalUnixShells + getPowerShellPaths()
  }

  fun getPowerShellPaths(): List<Path> {
    return listOf(
      "powershell",
      "powershell.exe",
      "pwsh",
      "pwsh.exe"
    ).mapNotNull {
      PathEnvironmentVariableUtil.findInPath(it)?.toPath()
    }
  }

  /**
   * To have stable tests, we need a reliable VT/ANSI sequences supplier.
   * Windows: let's restrict different Windows PTY-emulators to
   * require the bundled ConPTY library only.
   */
  fun assumeTestableProcess(shellEelProcess: ShellEelProcess) {
    val descriptor = shellEelProcess.eelApi.descriptor
    Assumptions.assumeFalse(
      descriptor.osFamily.isWindows && descriptor != LocalEelDescriptor,
      "Remote Windows may not support shell integration (latest ConPTY is required)"
    )
    val javaProcess = shellEelProcess.process
    if (javaProcess is WinPtyProcess || javaProcess is CygwinPtyProcess) {
      Assert.fail("Shell integration on Windows requires ConPTY, but ${javaProcess::class.java} was supplied")
    }
    if (javaProcess is WinConPtyProcess) {
      Assumptions.assumeTrue(javaProcess.isBundledConPtyLibrary, "Shell integration on Windows requires latest version of ConPTY")
    }
  }

  val ENTER_BYTES: ByteArray = byteArrayOf(Ascii.CR)
}

internal class TestTerminalSessionResult(
  val session: TerminalSession,
  val ttyConnector: TtyConnector,
)
