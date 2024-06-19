// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.classic.fixture

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.shellStartupOptions
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.BooleanSupplier

class TestShellSession(project: Project, parentDisposable: Disposable) {
  private val widget: ShellTerminalWidget
  private val watcher: TestTerminalBufferWatcher

  init {
    val settingsProvider = JBTerminalSystemSettingsProvider()
    widget = ShellTerminalWidget(project, settingsProvider, parentDisposable)
    watcher = TestTerminalBufferWatcher(widget.terminalTextBuffer, widget.terminal)
    start(project)
  }

  private fun start(project: Project) {
    val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
    val baseOptions = shellStartupOptions(project.basePath)
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    val process = runner.createProcess(configuredOptions)
    val connector: TtyConnector = PtyProcessTtyConnector(process, StandardCharsets.UTF_8)
    widget.start(connector)
  }

  @Throws(IOException::class)
  fun executeCommand(shellCommand: String) {
    widget.executeCommand(shellCommand)
  }

  fun awaitScreenLinesEndWith(expectedScreenLines: List<String>, timeoutMillis: Int) {
    watcher.awaitScreenLinesEndWith(expectedScreenLines, timeoutMillis.toLong())
  }

  fun awaitScreenLinesAre(expectedScreenLines: List<String>, timeoutMillis: Int) {
    watcher.awaitScreenLinesAre(expectedScreenLines, timeoutMillis.toLong())
  }

  fun awaitBufferCondition(condition: BooleanSupplier, timeoutMillis: Int) {
    watcher.awaitBuffer(condition, timeoutMillis.toLong())
  }

  val screenLines: String
    get() = watcher.screenLines
}
