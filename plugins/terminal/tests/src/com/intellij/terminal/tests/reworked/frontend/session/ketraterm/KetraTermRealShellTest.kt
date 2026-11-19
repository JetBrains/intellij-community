// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.frontend.session.ketraterm.localKetraTermDirectory
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil.resizeEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.TestApplication
import io.github.ketraterm.input.event.TerminalKey
import io.github.ketraterm.input.event.TerminalKeyEvent
import io.github.ketraterm.session.TerminalInputAdmission
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
internal class KetraTermRealShellTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("shells")
  fun `PowerShell evaluates pasted lines in clipboard order`(shellPath: Path?): Unit = runShell(shellPath) { session, events ->
    val text = "@(\r\n'first'\r\n'second'\r\n) -join ':'"
    assertThat(session.sendText(TerminalSendTextOptions(text, shouldExecute = false, useBracketedPasteMode = true))).isTrue()
    assertThat(session.terminalSession.submitInput(TerminalKeyEvent.key(TerminalKey.ENTER))).isEqualTo(TerminalInputAdmission.ACCEPTED)
    session.awaitContent("first:second")
    events.awaitEvent("shell prompt after paste") { it == TerminalPromptFinishedEvent }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shells")
  fun `PowerShell completion uses the native session request`(shellPath: Path?): Unit = runShell(shellPath) { session, events ->
    assertThat(session.sendText(TerminalSendTextOptions("Get-Ch", false, false))).isTrue()
    val native = session.terminalSession
    native.activeShellCommandLineRevision.first {
      native.activeShellCommandLine()?.let { it.commandText.startsWith("Get-Ch") && it.cursorOffset == 6 } == true
    }
    val command = requireNotNull(native.activeShellCommandLine())
    val result = requireNotNull(native.requestShellCompletions(command.commandText, command.cursorOffset))
    assertThat(result.candidates.map { it.replacementText }).contains("Get-ChildItem")
    assertThat(session.sendText(TerminalSendTextOptions("x", false, false))).isTrue()
    val resumed = withTimeoutOrNull(5.seconds) {
      native.activeShellCommandLineRevision.first {
        native.activeShellCommandLine()?.let { it.commandText.startsWith("Get-Chx") && it.cursorOffset == 7 } == true
      }
    }
    check(resumed != null) { "The shell did not process input after completion" }
    session.getInputChannel().send(TerminalWriteBytesEvent(byteArrayOf(3)))
    events.awaitEvent("shell prompt after cancellation") { it == TerminalPromptFinishedEvent }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shells")
  fun `PowerShell metadata alternate screen resize and process exit`(shellPath: Path?): Unit = runShell(shellPath) { session, events ->
    val command = "Write-Output 'KETRA-COMMAND-OK'"
    assertThat(session.sendText(TerminalSendTextOptions(command, shouldExecute = true, useBracketedPasteMode = false))).isTrue()
    val started = events.awaitEvent("command start") { it is TerminalCommandStartedEvent } as TerminalCommandStartedEvent
    assertThat(started.command).isEqualTo(command)
    val finished = events.awaitEvent("command completion") { it is TerminalCommandFinishedEvent } as TerminalCommandFinishedEvent
    assertThat(finished.exitCode).isZero()
    events.awaitEvent("shell prompt") { it == TerminalPromptFinishedEvent }

    val program = "[Console]::Write([char]27 + '[?1049hKETRA-ALT'); " +
                  "[Console]::ReadKey(\$true) | Out-Null; [Console]::Write([char]27 + '[?1049l')"
    assertThat(session.sendText(TerminalSendTextOptions(program, shouldExecute = true, useBracketedPasteMode = false))).isTrue()
    events.awaitEvent("alternate screen entry") { it is TerminalStateChangedEvent && it.state.isAlternateScreenBuffer }
    assertThat(session.terminalSession.activeShellCommandLine()).isNull()
    session.getInputChannel().send(resizeEvent(TerminalGridSize(100, 32)))
    session.terminalSession.encodeKey(TerminalKeyEvent.key(TerminalKey.ENTER))
    events.awaitEvent("alternate screen exit") { it is TerminalStateChangedEvent && !it.state.isAlternateScreenBuffer }
    events.awaitEvent("shell prompt") { it == TerminalPromptFinishedEvent }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shells")
  fun `Neovim edits a scratch buffer resizes and returns to the shell`(shellPath: Path?) {
    val executable = System.getProperty("ketraterm.test.tui") ?: System.getenv("KETRATERM_TEST_TUI")
    Assumptions.assumeTrue(executable != null, "Set KETRATERM_TEST_TUI to the Neovim executable")
    require(Files.isRegularFile(Path.of(requireNotNull(executable)))) { "The configured Neovim executable does not exist: $executable" }

    runShell(shellPath) { session, events ->
      val command = "& '${executable.replace("'", "''")}' --clean -n -i NONE " +
                    "-c 'set laststatus=2 statusline=KETRA-NVIM-SIZE-%{&columns}x%{&lines}'"
      assertThat(session.sendText(TerminalSendTextOptions(command, shouldExecute = true, useBracketedPasteMode = false))).isTrue()
      val started = events.awaitEvent("command start") { it is TerminalCommandStartedEvent } as TerminalCommandStartedEvent
      assertThat(started.command).isEqualTo(command)
      events.awaitEvent("alternate screen entry") { it is TerminalStateChangedEvent && it.state.isAlternateScreenBuffer }
      assertThat(session.terminalSession.activeShellCommandLine()).isNull()

      val marker = "KETRA-NVIM-${UUID.randomUUID()}"
      assertThat(session.terminalSession.submitInput(TerminalKeyEvent.text("i$marker"))).isEqualTo(TerminalInputAdmission.ACCEPTED)
      session.awaitContent(marker)
      for (size in listOf(TerminalGridSize(100, 32), TerminalGridSize(67, 19), TerminalGridSize(80, 24))) {
        session.getInputChannel().send(resizeEvent(size))
        assertThat(session.terminalSession.submitInput(listOf(
          TerminalKeyEvent.key(TerminalKey.ESCAPE),
          TerminalKeyEvent.text(":redraw!"),
          TerminalKeyEvent.key(TerminalKey.ENTER),
        ))).isEqualTo(TerminalInputAdmission.ACCEPTED)
        session.awaitContent("KETRA-NVIM-SIZE-${size.columns}x${size.rows}", size)
      }
      assertThat(session.terminalSession.submitInput(TerminalKeyEvent.text("A-RESIZED"))).isEqualTo(TerminalInputAdmission.ACCEPTED)
      session.awaitContent("$marker-RESIZED")

      assertThat(session.terminalSession.submitInput(listOf(
        TerminalKeyEvent.key(TerminalKey.ESCAPE),
        TerminalKeyEvent.text(":qa!"),
        TerminalKeyEvent.key(TerminalKey.ENTER),
      ))).isEqualTo(TerminalInputAdmission.ACCEPTED)
      events.awaitEvent("alternate screen exit") { it is TerminalStateChangedEvent && !it.state.isAlternateScreenBuffer }
      val finished = events.awaitEvent("command completion") { it is TerminalCommandFinishedEvent } as TerminalCommandFinishedEvent
      assertThat(finished.exitCode).isZero()
      events.awaitEvent("shell prompt") { it == TerminalPromptFinishedEvent }
    }
  }

  private fun runShell(
    shellPath: Path?,
    test: suspend (KetraTermTerminalSession, Channel<TerminalOutputEvent>) -> Unit,
  ): Unit = timeoutRunBlocking(60.seconds) {
    Assumptions.assumeTrue(shellPath != null, "PowerShell is unavailable; set KETRATERM_TEST_SHELL to its executable")
    val scope = childScope("KetraTerm real shell", Dispatchers.Default)
    val directory = Path.of(System.getProperty("user.home"))
    val options = ShellStartupOptions.Builder()
      .shellCommand(TerminalSessionTestUtil.createShellCommand(requireNotNull(shellPath).toString()))
      .workingDirectory(directory.toString())
      .envVariables(mapOf("PATHEXT" to (System.getenv("PATHEXT") ?: ".COM;.EXE;.BAT;.CMD")))
      .emulatorType(TerminalEmulatorType.KetraTerm)
      .build()
    val result = withContext(Dispatchers.IO) {
      TerminalSessionTestUtil.startTestTerminalSession(projectFixture.get(), options, isLowLevelSession = true, scope)
    }
    val session = result.session as KetraTermTerminalSession
    val events = Channel<TerminalOutputEvent>(Channel.UNLIMITED)
    val documentContentReceived = AtomicBoolean()
    val collector = launch {
      session.getOutputFlow().collect { batch ->
        for (event in batch) {
          if (event is TerminalContentUpdatedEvent) documentContentReceived.set(true)
          events.send(event)
        }
      }
    }
    try {
      events.awaitEvent("initial shell prompt") { it == TerminalPromptFinishedEvent }
      assertThat(Path.of(requireNotNull(localKetraTermDirectory(session.shellState.currentWorkingDirectoryUri()!!)))).isEqualTo(directory)
      assertThat(session.terminalSession.activeShellCommandLine()).isNotNull()
      test(session, events)
      assertThat(session.terminalSession.activeShellCommandLine()).isNotNull()
      assertThat(session.sendText(TerminalSendTextOptions("exit", shouldExecute = true, useBracketedPasteMode = false))).isTrue()
      events.awaitEvent("process exit") { it == TerminalSessionTerminatedEvent }
      assertThat(session.isClosed).isTrue()
      assertThat(result.ttyConnector.isConnected).isFalse()
      assertThat(documentContentReceived.get()).isFalse()
    }
    finally {
      result.ttyConnector.close()
      scope.cancel()
      collector.cancel()
      events.cancel()
    }
  }

  private suspend fun KetraTermTerminalSession.awaitContent(
    text: String,
    size: TerminalGridSize? = null,
  ) {
    var lastScreen = ""
    val received = withTimeoutOrNull(20.seconds) {
      terminalSession.renderGeneration.first {
        var found = false
        terminalSession.readRenderFrame { frame ->
          val codes = IntArray(frame.columns)
          val attrs = LongArray(frame.columns)
          val flags = IntArray(frame.columns)
          lastScreen = buildString {
            for (row in 0 until frame.rows) {
              frame.copyLine(row, codes, attrWords = attrs, flags = flags)
              for (code in codes) if (code > 0) appendCodePoint(code)
              append('\n')
            }
          }
          found = text in lastScreen && (size == null || frame.columns == size.columns && frame.rows == size.rows)
        }
        found
      }
    }
    check(received != null) { "No native content $text at $size within 20 seconds. Last screen:\n$lastScreen" }
  }

  private suspend fun Channel<TerminalOutputEvent>.awaitEvent(
    description: String,
    predicate: (TerminalOutputEvent) -> Boolean,
  ): TerminalOutputEvent {
    val recent = ArrayDeque<String>()
    return withTimeoutOrNull(20.seconds) {
      var match: TerminalOutputEvent? = null
      while (match == null) {
        val event = receive()
        if (recent.size == 32) recent.removeFirst()
        recent.addLast(when (event) {
          is TerminalContentUpdatedEvent -> "Content: ${event.text.takeLast(1200).replace("\n", "\\n").replace("\r", "\\r")}"
          else -> event.toString().take(1200)
        })
        if (predicate(event)) match = event
      }
      match
    } ?: error("No $description within 20 seconds. Recent events:\n${recent.joinToString("\n")}")
  }

  companion object {
    private val projectFixture = projectFixture()

    @JvmStatic
    fun shells(): List<Path?> {
      val explicit = System.getProperty("ketraterm.test.shell") ?: System.getenv("KETRATERM_TEST_SHELL")
      if (explicit != null) {
        val path = Path.of(explicit)
        require(Files.isRegularFile(path)) { "The configured PowerShell executable does not exist: $path" }
        return listOf(path)
      }
      return TerminalSessionTestUtil.getPowerShellPaths().distinct().ifEmpty { listOf(null) }
    }
  }
}
