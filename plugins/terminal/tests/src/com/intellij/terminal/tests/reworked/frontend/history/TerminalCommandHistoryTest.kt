// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.history

import com.intellij.openapi.application.EDT
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
internal class TerminalCommandHistoryTest {
  @Test
  fun `commands are normalized without a history file`(): Unit = timeoutRunBlocking {
    val scope = childScope("terminal command history")
    try {
      withContext(Dispatchers.EDT) {
        val integration = createIntegration(scope)
        integration.execute("  git status\t")
        integration.execute(" \t ")
        integration.execute("echo first\nsecond")
        assertThat(integration.commandHistory()).containsExactly("git status", "echo first\\nsecond")
      }
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun `current commands are bounded without a history file`(): Unit = timeoutRunBlocking {
    val scope = childScope("bounded terminal command history")
    try {
      withContext(Dispatchers.EDT) {
        val integration = createIntegration(scope)
        repeat(1501) { integration.execute("command $it") }
        assertThat(integration.commandHistory()).containsExactlyElementsOf((1..1500).map { "command $it" })
      }
    }
    finally {
      scope.cancel()
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 1000])
  fun `file history precedes current commands`(currentCount: Int, @TempDir directory: Path): Unit = timeoutRunBlocking {
    val file = directory.resolve("history")
    Files.writeString(file, (0..1000).joinToString("\n") { "old $it" })
    val scope = childScope("loaded terminal command history")
    try {
      val integration = withContext(Dispatchers.EDT) {
        createIntegration(scope).also {
          it.execute("current 0")
          it.onCommandHistoryFilePathReceived(file.toString())
          for (index in 1 until currentCount) it.execute("current $index")
          assertThat(it.commandHistory()).hasSize(currentCount)
        }
      }
      val retainedFileCount = minOf(1000, 1500 - currentCount)
      while (withContext(Dispatchers.EDT) { integration.commandHistory().size } == currentCount) delay(10)
      withContext(Dispatchers.EDT) {
        val expected = (1001 - retainedFileCount..1000).map { "old $it" } + (0 until currentCount).map { "current $it" }
        assertThat(integration.commandHistory()).containsExactlyElementsOf(expected)
        integration.execute("after loading")
        assertThat(integration.commandHistory()).containsExactlyElementsOf((expected + "after loading").takeLast(1500))
      }
    }
    finally {
      scope.cancel()
    }
  }

  private fun createIntegration(scope: CoroutineScope): TerminalShellIntegrationImpl = TerminalShellIntegrationImpl(
    TerminalTestUtil.createOutputModel(), TerminalSessionModelImpl(), scope, LocalEelDescriptor, ShellName.BASH,
  )

  private fun TerminalShellIntegrationImpl.execute(command: String) {
    onPromptStarted(TerminalOffset.ZERO)
    onPromptFinished(TerminalOffset.ZERO)
    onCommandStarted(TerminalOffset.ZERO, command)
    onCommandFinished(0)
  }
}
