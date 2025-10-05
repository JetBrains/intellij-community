// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.classic

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.intellij.util.io.delete
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.windows.conpty.WinConPtyProcess
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.shellStartupOptions
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.BooleanSupplier

/**
 * Use this class for testing Classic Terminal scenarios.
 *
 * It starts the terminal session with the provided shell command and attaches it to the [ShellTerminalWidget].
 * Allowing executing commands and checking the output state.
 */
@ApiStatus.Experimental
class ClassicTerminalTestShellSession(shellCommand: List<String>?, val widget: ShellTerminalWidget) {

  constructor(project: Project, parentDisposable: Disposable): this(null, ShellTerminalWidget(project, JBTerminalSystemSettingsProvider(), parentDisposable))

  private val watcher: ClassicTerminalTestBufferWatcher = ClassicTerminalTestBufferWatcher(widget.terminalTextBuffer, widget.terminal)

  init {
    start(shellCommand, widget)
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

  companion object {
    private fun start(shellCommand: List<String>?, terminalWidget: JBTerminalWidget) {
      val runner = LocalTerminalDirectRunner.createTerminalRunner(terminalWidget.project)
      val baseOptions = shellStartupOptions(terminalWidget.project.basePath) {
        it.shellCommand = shellCommand
      }
      val initialTermSize = TermSize(80, 50)
      val workingDirectory = Files.createTempDirectory("intellij-terminal-working-dir")
      val configuredOptions = runner.configureStartupOptions(baseOptions).builder().modify {
        it.initialTermSize = initialTermSize
        it.workingDirectory = workingDirectory.toString()
      }.build()
      val process = runner.createProcess(configuredOptions)
      val connector: TtyConnector = PtyProcessTtyConnector(process, StandardCharsets.UTF_8)
      terminalWidget.asNewWidget().connectToTty(connector, initialTermSize)

      Disposer.register(terminalWidget) {
        try {
          connector.close()
        }
        catch (t: Throwable) {
          logger<ClassicTerminalTestShellSession>().error("Error closing TtyConnector", t)
        }
        workingDirectory.delete()
      }

      if (SystemInfo.isWindows) {
        val msg = "On Windows, the bundled ConPTY in required for test stability"
        if (process !is WinConPtyProcess) {
          throw IllegalStateException(msg + ", but got " + process::class.java)
        }
        if (!process.isBundledConPtyLibrary) {
          throw IllegalStateException(msg)
        }
      }
    }
  }

}
