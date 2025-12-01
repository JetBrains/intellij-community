package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.CommandLineUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil.ENTER_BYTES
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.setValueInTest
import com.intellij.terminal.tests.reworked.util.withShellPathAndShellIntegration
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.PathUtil
import com.intellij.util.asDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.*
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.ShellNameUtil
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Run with `intellij.idea.ultimate.tests.main` classpath to test in WSL/Docker.
 * This classpath is used on the buildserver, so WSL/Docker are tested there.
 *
 * If ijent binaries are missing locally (typically on macOS), refer to IJPL-197291 for resolution.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@DockerTest(image = "ubuntu:24.04" /* image with `ps` command */, mandatory = false)
@ParameterizedClass
class TerminalSessionHasRunningCommandsTest(private val eelHolder: EelHolder) {

  private val project: Project by projectFixture()

  private val eelApi: EelApi
    get() = eelHolder.eel

  @TestFactory
  fun `default scenario (no,yes,no)`() = withShellPathAndShellIntegration(eelApi, 60.seconds) { shellPath, shellIntegration ->
    TerminalOptionsProvider.instance::shellIntegration.setValueInTest(shellIntegration, this.asDisposable())
    val session = TerminalSessionTestUtil.startTestTerminalSession(project, shellPath.toString(), null, childScope("TerminalSession"))
    val outputHandler = TestTerminalOutputHandler(session, this)
    sendEchoCommandAndAwaitItsCompletion(outputHandler, shellPath, GREETING)
    Assertions.assertThat(session.isClosed).isFalse()
    awaitNoCommandRunning(outputHandler, "echo $GREETING")
    val enterNamePrompt = "Please enter your name: "
    val askNameCommand = createAskNameCommand(enterNamePrompt)
    session.getInputChannel().sendCommandToExecute(askNameCommand, shellPath)
    outputHandler.awaitOutput(20.seconds) { output ->
      Assertions.assertThat(output).contains("\n" + enterNamePrompt)
    }
    Assertions.assertThat(session.hasRunningCommands())
      .describedAs(outputHandler.getAllOutput())
      .isTrue()
    session.getInputChannel().sendStringAndHitEnter("IntelliJ")
    outputHandler.awaitOutput(20.seconds) { output ->
      Assertions.assertThat(output).contains("Welcome, IntelliJ")
    }
    awaitNoCommandRunning(outputHandler, askNameCommand)
    Assertions.assertThat(session.isClosed).isFalse()
    session.getInputChannel().send(TerminalCloseEvent())
  }

  /**
   * Async prompt spawns `(zsh)` and `(git)` child processes after command completion:
   * https://github.com/ohmyzsh/ohmyzsh?tab=readme-ov-file#async-git-prompt
   * We wait for these processes to terminate.
   */
  private suspend fun awaitNoCommandRunning(outputHandler: TestTerminalOutputHandler, lastCommand: String) {
    awaitCondition(20.seconds) {
      Assertions.assertThat(outputHandler.session.hasRunningCommands())
        .describedAs { "Command is running (last command: $lastCommand)\n--- OUTPUT ---\n" + outputHandler.getAllOutput() }
        .isFalse()
    }
  }

  private fun createAskNameCommand(@Suppress("SameParameterValue") prompt: String): String {
    return when (eelApi.platform.osFamily) {
      EelOsFamily.Posix -> {
        val shellCommand = $$"""
          printf "$$prompt" && read user_name && echo "Welcome, $user_name"
        """.trimIndent()
        // Wrap with "/bin/sh -c ..." to spawn a child process.
        // Because `TerminalUtil.hasRunningCommands` detects a running command on Unix,
        // when the shell process has at least one child process.
        "/bin/sh -c " + CommandLineUtil.posixQuote(shellCommand)
      }
      EelOsFamily.Windows -> {
        val powershellCommand = $$"""
          $UserName = Read-Host -Prompt "$${prompt.removeSuffix(": ")}"; Write-Output "Welcome, $UserName"
        """.trimIndent()
        // Wrap with "powershell.exe -Command ..." to spawn a new process attached to the console.
        // Because `TerminalUtil.hasRunningCommands` detects a running command on Windows,
        // when multiple processes are attached to the console.
        "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command { $powershellCommand }"
      }
    }
  }

  private suspend fun awaitCondition(timeout: Duration, assertCondition: suspend () -> Unit) {
    withTimeoutOrNull(timeout) {
      while (!runCatching { assertCondition() }.isSuccess) {
        delay(100.milliseconds)
      }
    }
    assertCondition()
  }
}

private suspend fun sendEchoCommandAndAwaitItsCompletion(
  outputHandler: TestTerminalOutputHandler,
  shellPath: EelPath,
  stringToEcho: String,
) {
  val command = "echo $stringToEcho"
  outputHandler.session.getInputChannel().sendCommandToExecute(command, shellPath)
  outputHandler.awaitOutput(20.seconds) { output ->
    val fullErrorMsg: (String) -> String = { "$it\n--- OUTPUT ---\n$output" }
    val commandStartInd = output.indexOf(command)
    if (commandStartInd < 0) {
      throw AssertionError(fullErrorMsg("Unable to find '$command'"))
    }
    val outputAtLineStart = "\n$stringToEcho\n"
    val outputStartInd = output.indexOf(outputAtLineStart, commandStartInd + command.length)
    if (outputStartInd < 0) {
      throw AssertionError(fullErrorMsg("Unable to find '\\n$stringToEcho\\n'"))
    }
    if (output.substring(outputStartInd + outputAtLineStart.length).isBlank()) {
      throw AssertionError(fullErrorMsg("Expected non-empty prompt after '$stringToEcho'"))
    }
  }
}

private class TestTerminalOutputHandler(val session: TerminalSession, coroutineScope: CoroutineScope) {

  private val outputModel: MutableTerminalOutputModel = TerminalTestUtil.createOutputModel()
  private val lock = ReentrantLock()
  private val terminatedDeferred = CompletableDeferred<Unit>()

  init {
    val job = coroutineScope.launch {
      session.getOutputFlow().collect { events ->
        events.filterIsInstance<TerminalInitialStateEvent>().firstOrNull()?.let {
          outputModel.restoreFromState(it.outputModelState.toState())
        }
        lock.withLock {
          events.filterIsInstance<TerminalContentUpdatedEvent>().forEach(outputModel::updateContent)
        }
        if (events.contains(TerminalSessionTerminatedEvent)) {
          terminatedDeferred.complete(Unit)
        }
      }
    }
    terminatedDeferred.invokeOnCompletion {
      job.cancel()
    }
  }

  suspend fun awaitOutput(timeout: Duration, assertCondition: (output: String) -> Unit) {
    try {
      withTimeout(timeout) {
        doAwaitCondition(assertCondition)
      }
    }
    catch (e: TimeoutCancellationException) {
      System.err.println(e.message)
      assertCondition(getAllOutput())
      Assertions.fail(e)
    }
  }

  fun getAllOutput(): String {
    return (if (terminatedDeferred.isCompleted) "!!! TERMINATED !!!\n" else "") +
           lock.withLock { outputModel.document.text }
  }

  private suspend fun doAwaitCondition(assertCondition: (output: String) -> Unit) {
    val conditionHolds: () -> Boolean = {
      val output = getAllOutput()
      runCatching { assertCondition(output) }.isSuccess
    }
    suspendCancellableCoroutine { continuation ->
      lock.withLock {
        if (conditionHolds()) {
          continuation.resume(Unit)
        }
        else {
          val disposable = Disposer.newDisposable()
          continuation.invokeOnCancellation { Disposer.dispose(disposable) }
          outputModel.addListener(disposable, object : TerminalOutputModelListener {
            override fun afterContentChanged(event: TerminalContentChangeEvent) {
              if (conditionHolds()) {
                Disposer.dispose(disposable)
                continuation.resume(Unit)
              }
            }
          })
        }
      }
    }
  }
}

private suspend fun SendChannel<TerminalInputEvent>.sendCommandToExecute(command: String, shellPath: EelPath) {
  val resultCommand = when (PathUtil.getFileName(shellPath.toString())) {
    // prefix with a space to prevent adding it to history for Zsh/Bash/fish (works by default)
    ShellNameUtil.BASH_NAME, ShellNameUtil.ZSH_NAME, ShellNameUtil.FISH_NAME -> " $command"
    else -> command
  }
  this.sendStringAndHitEnter(resultCommand)
}

private suspend fun SendChannel<TerminalInputEvent>.sendStringAndHitEnter(str: String) {
  this.send(TerminalWriteBytesEvent(str.toByteArray(Charsets.UTF_8) + ENTER_BYTES))
}

private val GREETING: String = ('A'..'Z').joinToString(separator = "")
