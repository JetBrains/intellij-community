// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.util

import com.intellij.execution.Platform
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.LocalProcessService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.tests.block.testApps.LINE_SEPARATOR
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.EnvironmentUtil
import com.intellij.util.asSafely
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalCustomCommandListener
import com.pty4j.windows.conpty.WinConPtyProcess
import com.pty4j.windows.cygwin.CygwinPtyProcess
import com.pty4j.windows.winpty.WinPtyProcess
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.session.*
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Assert
import org.junit.Assume
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

internal object TerminalSessionTestUtil {

  fun startBlockTerminalSession(
    project: Project,
    shellPath: String,
    parentDisposable: Disposable,
    initialTermSize: TermSize = TermSize(200, 20),
    disableSavingHistory: Boolean = true,
    terminalCustomCommandListener: TerminalCustomCommandListener = TerminalCustomCommandListener {}
  ): BlockTerminalSession {
    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.NEW_TERMINAL, parentDisposable)
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_FISH_REGISTRY).setValue(true, parentDisposable)
    val runner = LocalBlockTerminalRunner(project)
    val baseOptions = ShellStartupOptions.Builder().shellCommand(listOf(shellPath)).initialTermSize(initialTermSize)
      .envVariables(listOfNotNull(
        EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true",
        ("HISTFILE" to "/dev/null").takeIf { disableSavingHistory }
      ).toMap())
      .build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    assumeBlockShellIntegration(configuredOptions)
    val process = runner.createProcess(configuredOptions)
    val ttyConnector = runner.createTtyConnector(process)

    val session = BlockTerminalSession(runner.settingsProvider, BlockTerminalColorPalette(), configuredOptions.shellIntegration!!)
    Disposer.register(parentDisposable) {
      Disposer.dispose(session)
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        fail("Shell hasn't been terminated within timeout, pid:${process.pid()}")
      }
    }
    session.controller.resize(initialTermSize, RequestOrigin.User)
    val model: TerminalModel = session.model
    session.controller.addCustomCommandListener(terminalCustomCommandListener)

    val initializedFuture = CompletableFuture<Boolean>()
    val listenersDisposable = Disposer.newDisposable()
    session.addCommandListener(object : ShellCommandListener {
      override fun initialized() {
        initializedFuture.complete(true)
      }
    }, listenersDisposable)

    session.start(ttyConnector)

    if (process is WinPtyProcess || process is CygwinPtyProcess) {
      Assert.fail("Shell integration on Windows requires ConPTY, but " + process::class.java)
    }
    if (process is WinConPtyProcess) {
      Assume.assumeTrue("Shell integration on Windows requires latest version of ConPTY", process.isBundledConPtyLibrary)
    }

    try {
      initializedFuture.get(30_000, TimeUnit.MILLISECONDS)
    }
    catch (_: TimeoutException) {
      BasePlatformTestCase.fail(
        "Session failed to initialize" +
        ", size: ${model.width}x${model.height}" +
        ", process: ${process::class.java.name}" +
        ", bundled-ConPTY: ${process.asSafely<WinConPtyProcess>()?.isBundledConPtyLibrary}" +
        ", command: ${LocalProcessService.getInstance().getCommand(process)}" +
        ", text buffer:\n${model.withContentLock { model.getAllText() }}" +
        ", envs: ${configuredOptions.envVariables}")
    }
    finally {
      Disposer.dispose(listenersDisposable)
    }

    if (disableSavingHistory) {
      disableSavingHistory(session)
    }

    return session
  }

  fun disableSavingHistory(session: BlockTerminalSession) {
    if (session.shellIntegration.shellType == ShellType.POWERSHELL) {
      val commandResultFuture = getCommandResultFuture(session)
      // Disable saving history for the session only, not persisting.
      session.commandExecutionManager.sendCommandToExecute("Set-PSReadlineOption -HistorySaveStyle SaveNothing")
      assertCommandResult(0, "", commandResultFuture)
    }
  }

  fun getCommandResultFuture(session: BlockTerminalSession): CompletableFuture<CommandResult> {
    val disposable = Disposer.newDisposable(session)
    val scraper = ShellCommandOutputScraperImpl(session)
    val lastOutput: AtomicReference<StyledCommandOutput?> = AtomicReference()
    scraper.addListener(object : ShellCommandOutputListener {
      override fun commandOutputChanged(output: StyledCommandOutput) {
        lastOutput.set(output)
      }
    }, disposable)
    val result: CompletableFuture<CommandResult> = CompletableFuture()
    session.commandManager.addListener(object : ShellCommandListener {
      override fun commandFinished(event: CommandFinishedEvent) {
        val (text, commandEndMarkerFound) = scraper.scrapeOutput()
        Assert.assertEquals(session.commandBlockIntegration.commandEndMarker != null, commandEndMarkerFound)
        result.complete(CommandResult(event.exitCode, text))
        Disposer.dispose(disposable)
      }
    }, disposable)
    return result
  }

  @Suppress("SameParameterValue")
  fun assertCommandResult(expectedExitCode: Int, expectedOutput: String, actualResultFuture: CompletableFuture<CommandResult>) {
    val actualResult = try {
      actualResultFuture.get(20_000, TimeUnit.MILLISECONDS)
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
    val actualOutput = normalizeActualOutput(actualResult.output).let {
      if (expectedOutput == it + LINE_SEPARATOR) it + LINE_SEPARATOR else it
    }
    Assert.assertEquals(stringify(expectedExitCode, expectedOutput), stringify(actualResult.exitCode, actualOutput))
  }

  private fun normalizeActualOutput(output: String): String {
    // Trim trailing whitespaces on Windows as ConPTY gets crazy sometimes
    return StringUtil.splitByLinesDontTrim(output).joinToString(LINE_SEPARATOR) { it.trimEnd() }
  }

  private fun stringify(exitCode: Int, output: String): String {
    return "exit_code:$exitCode, output: $output"
  }

  private fun assumeBlockShellIntegration(options: ShellStartupOptions) {
    Assume.assumeTrue("Block shell integration is expected", options.shellIntegration?.commandBlockIntegration != null)
  }

  fun getJavaCommand(mainClass: Class<*>, args: List<String>): List<String> {
    return listOf(getJavaExecutablePath().toString(),
                  "-cp",
                  getJarPathForClasses(listOf(mainClass, KotlinVersion::class.java /* kotlin-stdlib.jar */)),
                  mainClass.name) +
           args
  }

  private fun getJavaExecutablePath(): Path {
    return Path.of(System.getProperty("java.home"), "bin", if (Platform.current() == Platform.WINDOWS) "java.exe" else "java")
  }

  private fun getJarPathForClasses(classes: List<Class<*>>): String {
    return classes.joinToString(Platform.current().pathSeparator.toString()) {
      checkNotNull(PathManager.getJarPathForClass(it)) { "Cannot find jar/directory for $it" }
    }
  }

  fun BlockTerminalSession.sendCommandToExecuteWithoutAddingToHistory(shellCommand: List<String>) {
    val commandline = when (this.shellIntegration.shellType) {
      ShellType.POWERSHELL -> toPowerShellCommandLine(shellCommand)
      else -> toPosixShellCommandLine(shellCommand)
    }
    this.sendCommandlineToExecuteWithoutAddingToHistory(commandline)
  }

  fun List<String>.toCommandLine(session: BlockTerminalSession, preventAddingToHistory: Boolean = true): String {
    return when (session.shellIntegration.shellType) {
      ShellType.POWERSHELL -> {
        // saving command history is disabled for PowerShell in [disableSavingHistory]
        toPowerShellCommandLine(this)
      }
      else -> {
        val commandLine = toPosixShellCommandLine(this)
        // prefix with a space to prevent adding it to history for Zsh/Bash/fish (works by default)
        if (preventAddingToHistory) " $commandLine" else commandLine
      }
    }
  }

  fun BlockTerminalSession.sendCommandlineToExecuteWithoutAddingToHistory(shellCommandline: String) {
    if (this.shellIntegration.shellType == ShellType.POWERSHELL) {
      // saving command history is disabled for PowerShell in [disableSavingHistory]
      this.commandExecutionManager.sendCommandToExecute(shellCommandline)
    }
    else {
      // add a leading space to prevent adding to global shell history
      this.commandExecutionManager.sendCommandToExecute(" $shellCommandline")
    }
  }

  private fun toPowerShellCommandLine(command: List<String>): String {
    return "&" + command.joinToString(" ") {
      if (it.isEmpty()) {
        fail("An empty parameter cannot be escaped uniformly in both PowerShell 5.1 and PowerShell 7.4")
      }
      else {
        StringUtil.wrapWithDoubleQuote(StringUtil.escapeBackSlashes(it))
      }
    }
  }

  private fun toPosixShellCommandLine(command: List<String>): String = ParametersListUtil.join(command)

  fun getShellPaths(): List<Path> {
    return listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/usr/bin/bash",
      "/usr/local/bin/bash",
      "/opt/homebrew/bin/bash",
      // Disable fish because it fails tests on merge requests
      //"/bin/fish",
      //"/usr/bin/fish",
      //"/usr/local/bin/fish",
      //"/opt/homebrew/bin/fish",
      "/bin/pwsh",
      "/usr/bin/pwsh",
      "/usr/local/bin/pwsh",
      "/opt/homebrew/bin/pwsh",
      "powershell.exe",
      "pwsh.exe",
    ).mapNotNull {
      val path = Path.of(it)
      if (Files.isRegularFile(path)) path else PathEnvironmentVariableUtil.findInPath(it)?.toPath()
    }
  }
}

data class CommandResult(val exitCode: Int, val output: String)
