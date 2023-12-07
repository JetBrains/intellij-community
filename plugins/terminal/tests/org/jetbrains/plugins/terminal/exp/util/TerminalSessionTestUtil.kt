// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.util

import com.intellij.execution.Platform
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.EnvironmentUtil
import com.intellij.util.LineSeparator
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.exp.*
import org.jetbrains.plugins.terminal.exp.ShellCommandOutputListener
import org.jetbrains.plugins.terminal.exp.ShellCommandOutputScraper
import org.jetbrains.plugins.terminal.exp.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Assert
import org.junit.Assume
import org.junit.jupiter.api.fail
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

object TerminalSessionTestUtil {
  fun startBlockTerminalSession(project: Project,
                                shellPath: String,
                                parentDisposable: Disposable,
                                initialTermSize: TermSize = TermSize(200, 20)): BlockTerminalSession {
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(true, parentDisposable)
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_FISH_REGISTRY).setValue(true, parentDisposable)
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_POWERSHELL_REGISTRY).setValue(true, parentDisposable)
    val runner = LocalBlockTerminalRunner(project)
    val baseOptions = ShellStartupOptions.Builder().shellCommand(listOf(shellPath)).initialTermSize(initialTermSize)
      .envVariables(mapOf(EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true"))
      .build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    assumeBlockShellIntegration(configuredOptions)
    val process = runner.createProcess(configuredOptions)
    val ttyConnector = runner.createTtyConnector(process)

    val colorPalette = BlockTerminalColorPalette(EditorColorsManager.getInstance().globalScheme)
    val session = BlockTerminalSession(runner.settingsProvider, colorPalette, configuredOptions.shellIntegration!!)
    Disposer.register(parentDisposable, session)
    session.controller.resize(initialTermSize, RequestOrigin.User)
    val model: TerminalModel = session.model

    val initializedFuture = CompletableFuture<Boolean>()
    val listenersDisposable = Disposer.newDisposable()
    session.addCommandListener(object : ShellCommandListener {
      override fun initialized(currentDirectory: String?) {
        initializedFuture.complete(true)
      }
    }, listenersDisposable)

    session.start(ttyConnector)

    try {
      initializedFuture.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (ex: TimeoutException) {
      BasePlatformTestCase.fail(
        "Session failed to initialize, size: ${model.height}x${model.width}, text buffer:\n${model.withContentLock { model.getAllText() }}")
    }
    finally {
      Disposer.dispose(listenersDisposable)
    }

    disableSavingHistory(session)

    return session
  }

  private fun disableSavingHistory(session: BlockTerminalSession) {
    if (session.shellIntegration.shellType == ShellType.POWERSHELL) {
      val commandResultFuture = getCommandResultFuture(session)
      // Disable saving history for the session only, not persisting.
      session.commandManager.sendCommandToExecute("Set-PSReadlineOption -HistorySaveStyle SaveNothing")
      assertCommandResult(0, "", commandResultFuture)
    }
  }

  fun getCommandResultFuture(session: BlockTerminalSession): CompletableFuture<CommandResult> {
    val disposable = Disposer.newDisposable(session)
    val scraper = ShellCommandOutputScraper(session)
    val lastOutput: AtomicReference<StyledCommandOutput?> = AtomicReference()
    scraper.addListener(object : ShellCommandOutputListener {
      override fun commandOutputChanged(output: StyledCommandOutput) {
        lastOutput.set(output)
      }
    }, disposable)
    val result: CompletableFuture<CommandResult> = CompletableFuture()
    session.commandManager.addListener(object : ShellCommandListener {
      override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
        val (text, commandEndMarkerFound) = scraper.scrapeOutput()
        Assert.assertEquals(session.commandBlockIntegration.commandEndMarker != null, commandEndMarkerFound)
        result.complete(CommandResult(exitCode, text))
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
    var actualOutput = StringUtil.splitByLinesDontTrim(actualResult.output).joinToString("\n") { it.trimEnd() }
    val expectedOutputWithLF = StringUtil.convertLineSeparators(expectedOutput, LineSeparator.LF.separatorString)
    if (expectedOutputWithLF == actualOutput + "\n") {
      actualOutput += "\n"
    }
    Assert.assertEquals(stringify(expectedExitCode, expectedOutputWithLF), stringify(actualResult.exitCode, actualOutput))
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

  fun BlockTerminalSession.sendCommandlineToExecuteWithoutAddingToHistory(shellCommandline: String) {
    if (this.shellIntegration.shellType == ShellType.POWERSHELL) {
      // saving command history is disabled for PowerShell in [disableSavingHistory]
      this.commandManager.sendCommandToExecute(shellCommandline)
    }
    else {
      // add a leading space to prevent adding to global shell history
      this.commandManager.sendCommandToExecute(" $shellCommandline")
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
}

data class CommandResult(val exitCode: Int, val output: String)
