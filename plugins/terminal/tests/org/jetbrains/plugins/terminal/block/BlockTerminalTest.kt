// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.block.testApps.SimpleTextRepeater
import org.jetbrains.plugins.terminal.exp.*
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.junit.Assert
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(Parameterized::class)
class BlockTerminalTest(private val shellPath: String) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<String> = listOf(
      "/bin/zsh",
      "/bin/bash",
      "/usr/local/bin/fish",
    ).filter { File(it).exists() }
  }

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @org.junit.Test
  fun `test echo output is read`() {
    val session = startBlockTerminalSession(TermSize(20, 10))
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    session.sendCommandToExecute("echo qqq")
    assertCommandResult(0, "qqq\n", outputFuture)
  }

  @org.junit.Test
  fun `test read from history and screen buffers`() {
    val termSize = TermSize(20, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val items = listOf(SimpleTextRepeater.Item("Hello, World", termSize.rows * 3),
                       SimpleTextRepeater.Item("Done", 1))
    session.sendCommandToExecute(SimpleTextRepeater.generateCommandLine(*items.toTypedArray()))
    val expectedLines = items.flatMap { item ->
      MutableList(item.count) { item.lineText }
    }
    val expectedOutput : String = expectedLines.joinToString("\n", postfix = "\n")
    assertCommandResult(0, expectedOutput, outputFuture)
  }

  private fun startBlockTerminalSession(termSize: TermSize) =
    TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath, disposableRule.disposable, termSize)

  @Suppress("SameParameterValue")
  private fun assertCommandResult(expectedExitCode: Int, expectedOutput: String, actualResultFuture: CompletableFuture<CommandResult>) {
    val actualResult = actualResultFuture.get(5000, TimeUnit.MILLISECONDS)
    var actualOutput = StringUtil.splitByLinesDontTrim(actualResult.output).joinToString("\n") { it.trimEnd() }
    if (expectedOutput == actualOutput + "\n") {
      actualOutput += "\n"
    }
    Assert.assertEquals(stringify(expectedExitCode, expectedOutput), stringify(actualResult.exitCode, actualOutput))
  }

  private fun stringify(exitCode: Int, output: String): String {
    return "exit_code:$exitCode, output: $output"
  }
}

fun getCommandResultFuture(session: TerminalSession): CompletableFuture<CommandResult> {
  val disposable = Disposer.newDisposable(session)
  val scraper = ShellCommandOutputScraper(session)
  val lastOutput: AtomicReference<StyledCommandOutput> = AtomicReference()
  scraper.addListener(object : ShellCommandOutputListener {
    override fun commandOutputChanged(output: StyledCommandOutput) {
      lastOutput.set(output)
    }
  }, disposable)
  val result: CompletableFuture<CommandResult> = CompletableFuture()
  session.commandManager.addListener(object : ShellCommandListener {
    override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
      result.complete(CommandResult(exitCode, lastOutput.get()?.text.orEmpty()))
      Disposer.dispose(disposable)
    }
  }, disposable)
  return result
}

data class CommandResult(val exitCode: Int, val output: String)