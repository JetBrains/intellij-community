// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ObservableTtyConnector
import com.intellij.terminal.frontend.session.ketraterm.createKetraTermTerminalSession
import com.intellij.terminal.frontend.session.ketraterm.localKetraTermDirectory
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.jediterm.core.util.TermSize
import io.github.ketraterm.session.TerminalSessionState
import io.github.ketraterm.workspace.TerminalProfile
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@TestApplication
@SystemProperty(propertyKey = "ketraterm.experimental.enableDocumentModel", propertyValue = "true")
internal class KetraTermNativeShellTest {
  @Test
  fun `native hooks preserve prompt and command boundaries`(@TempDir scripts: Path): Unit = timeoutRunBlocking {
    val scope = childScope("native shell test", Dispatchers.Default)
    val connector = LoopbackTtyConnector()
    val launch = TerminalShellLaunch.prepare(TerminalProfile("test", "test", listOf("pwsh")), scriptDirectory = scripts)
    val options = ShellStartupOptions.Builder().shellCommand(listOf("pwsh")).initialTermSize(TermSize(80, 24)).build()
    val session = createKetraTermTerminalSession(
      null, ObservableTtyConnector(connector), options, JBTerminalSystemSettingsProvider(), scope, shellLaunch = launch,
    )
    val events = Channel<TerminalOutputEvent>(Channel.UNLIMITED)
    scope.launch { session.getOutputFlow().collect { batch -> batch.forEach { events.send(it) } } }
    try {
      val directory = scripts.toUri().toASCIIString()
      val ready = "\u001b]633;KetraTermCompletion;3;Ready;${"a".repeat(32)}\u0007"
      val stream = "\u001b]7;$directory\u0007\u001b]133;A\u0007" + ready + "PS> \u001b]133;B\u0007echo native" +
                   "\u001b]133;C\u0007\r\noutput\r\n\u001b]133;D;7\u0007"
      stream.forEach { connector.feed(it.toString()) }
      var enabled = false
      var beforePrompt = ""
      var started = false
      while (true) {
        when (val event = events.receive()) {
          is TerminalStateChangedEvent -> enabled = event.state.isShellIntegrationEnabled
          is TerminalContentUpdatedEvent -> beforePrompt = event.text
          TerminalPromptFinishedEvent -> {
            assertThat(enabled).isTrue()
            assertThat(beforePrompt).contains("PS> ").doesNotContain("echo native")
          }
          is TerminalCommandStartedEvent -> {
            assertThat(event.command).isEqualTo("echo native")
            started = true
          }
          is TerminalCommandFinishedEvent -> {
            assertThat(started).isTrue()
            assertThat(event.command).isEqualTo("echo native")
            assertThat(event.exitCode).isEqualTo(7)
            assertThat(event.currentDirectory).isEqualTo(scripts.toString())
            break
          }
          else -> Unit
        }
      }
      assertThat(session.shellState.currentWorkingDirectoryUri()).isEqualTo(directory)
      assertThat(session.usesNativeShellIntegration).isTrue()
      assertThat(session.terminalSession.shellCompletionAvailable).isFalse()
      connector.feed("\u001b]133;A\u0007" + ready + "PS> \u001b]133;B\u0007")
      while (events.receive() != TerminalPromptFinishedEvent) { }
      assertThat(session.terminalSession.shellCompletionAvailable).isTrue()
      val nextDirectory = scripts.resolve("next directory")
      connector.feed("\u001b]7;${nextDirectory.toUri()}\u0007\u001b]133;A\u0007>\u001b]133;B\u0007")
      var platformDirectory: String? = null
      while (true) {
        when (val event = events.receive()) {
          is TerminalStateChangedEvent -> platformDirectory = event.state.currentDirectory
          TerminalPromptFinishedEvent -> break
          else -> Unit
        }
      }
      assertThat(platformDirectory).isEqualTo(nextDirectory.toString())
    }
    finally {
      scope.cancel()
      session.terminalSession.state.first { it is TerminalSessionState.Closed }
      connector.close()
      launch.close()
    }
  }

  @Test
  fun `session disposal removes native inventory resources`(@TempDir scripts: Path): Unit = timeoutRunBlocking {
    val scope = childScope("native inventory test", Dispatchers.Default)
    val launch = TerminalShellLaunch.prepare(TerminalProfile("bash", "Bash", listOf("bash")), scriptDirectory = scripts)
    val mailbox = Path.of(launch.profile.environment.getValue("KetraTerm_COMMAND_INVENTORY_DIRECTORY"))
    val connector = LoopbackTtyConnector()
    val options = ShellStartupOptions.Builder().shellCommand(listOf("bash")).initialTermSize(TermSize(80, 24)).build()
    val session = createKetraTermTerminalSession(null, connector, options, JBTerminalSystemSettingsProvider(), scope, shellLaunch = launch)
    scope.cancel()
    session.terminalSession.state.first { it is TerminalSessionState.Closed }
    scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
    assertThat(connector.isConnected).isFalse()
    assertThat(Files.exists(mailbox)).isFalse()
  }

  @Test
  fun `failed session construction closes the process and launch`(@TempDir scripts: Path): Unit = timeoutRunBlocking {
    val scope = childScope("failed native shell test", Dispatchers.Default)
    val launch = TerminalShellLaunch.prepare(TerminalProfile("bash", "Bash", listOf("bash")), scriptDirectory = scripts)
    val mailbox = Path.of(launch.profile.environment.getValue("KetraTerm_COMMAND_INVENTORY_DIRECTORY"))
    val connector = LoopbackTtyConnector()
    val options = ShellStartupOptions.Builder().shellCommand(listOf("bash")).build()
    assertThatThrownBy {
      createKetraTermTerminalSession(null, connector, options, JBTerminalSystemSettingsProvider(), scope, shellLaunch = launch)
    }.isInstanceOf(IllegalArgumentException::class.java)
    assertThat(connector.isConnected).isFalse()
    assertThat(Files.exists(mailbox)).isFalse()
  }

  @Test
  fun `local directory conversion rejects remote and malformed metadata`(@TempDir directory: Path) {
    assertThat(localKetraTermDirectory(directory.toUri().toString())).isEqualTo(directory.toString())
    assertThat(localKetraTermDirectory("file://remote.example/tmp")).isNull()
    assertThat(localKetraTermDirectory("file://remote!host/tmp")).isNull()
    assertThat(localKetraTermDirectory("file://user@localhost/tmp")).isNull()
    assertThat(localKetraTermDirectory("file://localhost/tmp?query")).isNull()
    assertThat(localKetraTermDirectory("ssh://host/tmp")).isNull()
    assertThat(localKetraTermDirectory("file://localhost/%invalid")).isNull()
  }
}
