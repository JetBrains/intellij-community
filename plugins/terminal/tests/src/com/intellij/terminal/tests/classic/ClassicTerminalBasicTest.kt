// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.tests.classic

import com.intellij.execution.CommandLineUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.util.io.delete
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.testFramework.classic.ClassicTerminalTestShellSession
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@RunWith(Parameterized::class)
class ClassicTerminalBasicTest(private val shellPath: Path) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> = TerminalSessionTestUtil.getShellPaths()
  }

  @Before
  fun setup() {
    if (SystemInfo.isWindows) {
      Assume.assumeTrue(SystemInfo.isWin11OrNewer)
    }
    else {
      // PowerShell ignores commands written to the process stdin at the shell startup.
      // Once the PowerShell is up and running (the command prompt is shown), commands can be written to stdin.
      Assume.assumeFalse(isPowerShell())
    }
  }

  @Test
  fun `basic echo and clear`() {
    val session = startSession()
    val command = if (isPowerShell()) {
      $$"$env:_MY_FOO = 'test'; echo \"1`n2`n$env:_MY_FOO\""
    }
    else {
      $$"_MY_FOO=test; echo -e \"1\\n2\\n$_MY_FOO\""
    }
    session.executeCommand(command)
    session.awaitScreenLinesEndWith(listOf("1", "2", "test"), 10000)
    session.executeCommand("clear")
    session.awaitScreenLinesAre(emptyList(), 10000)
  }

  @Test
  fun `commands should be executed in order`() {
    val outputFile = Files.createTempFile("output", ".txt")
    val widget = createWidget()
    val commandCount = 10
    for (i in 1..commandCount) {
      if (isPowerShell()) {
        // PowerShell 5.1 has UTF-16LE output encoding by default
        widget.executeCommand("Add-Content -Path '$outputFile' -Value $i -Encoding ASCII")
      }
      else {
        widget.executeCommand("echo " + i + " >> " + CommandLineUtil.posixQuote(outputFile.toString()))
      }
    }
    val session = startSession(widget)
    val finishMarker = "All commands have been executed"
    widget.executeWithTtyConnector {
      widget.executeCommand("echo " + CommandLineUtil.posixQuote(finishMarker))
    }
    session.awaitScreenLinesEndWith(listOf(finishMarker), 60_000)
    val actualOutputLines = Files.readAllLines(outputFile, StandardCharsets.US_ASCII)

    outputFile.delete()
    val expectedOutputLines: List<String> = (1..commandCount).map { it.toString() }
    Assert.assertEquals(expectedOutputLines, actualOutputLines)
  }

  private fun createWidget(): ShellTerminalWidget {
    return ShellTerminalWidget(projectRule.project, JBTerminalSystemSettingsProvider(), disposableRule.disposable)
  }

  private fun startSession(widget: ShellTerminalWidget = createWidget()): ClassicTerminalTestShellSession {
    return ClassicTerminalTestShellSession(listOf(shellPath.toString()), widget)
  }

  private fun isPowerShell(): Boolean {
    val fileName = shellPath.fileName.toString()
    return fileName == "pwsh" || fileName == "pwsh.exe" ||
           fileName == "powershell" || fileName == "powershell.exe"
  }
}
