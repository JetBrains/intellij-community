package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.CommandLineUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi.EnvironmentVariablesException
import com.intellij.platform.eel.EelExecApiHelpers
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.util.ShellEelProcess
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
    val (session, shellProcess) = startTerminalSession(shellPath, this)
    echoShellVersion(session, shellPath)
    val outputHandler = TestTerminalOutputHandler(session, this)
    sendEchoCommandAndAwaitItsCompletion(outputHandler, shellPath, GREETING)
    Assertions.assertThat(session.isClosed).isFalse()
    awaitNoCommandRunning(outputHandler, "echo $GREETING", shellProcess)
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
    awaitNoCommandRunning(outputHandler, askNameCommand, shellProcess)
    Assertions.assertThat(session.isClosed).isFalse()
    session.getInputChannel().send(TerminalCloseEvent())
  }

  private fun startTerminalSession(shellPath: EelPath, coroutineScope: CoroutineScope): Pair<TerminalSession, ShellEelProcess> {
    val sessionResult = TerminalSessionTestUtil.startTestTerminalSession(
      project,
      shellPath.toString(),
      null,
      coroutineScope.childScope("TerminalSession")
    )
    val ttyConnector = ShellTerminalWidget.getProcessTtyConnector(sessionResult.ttyConnector) ?: run {
      throw AssertionError("Unknown TtyConnector: ${sessionResult.ttyConnector}")
    }
    val shellEelProcess = (ttyConnector as LocalTerminalTtyConnector).shellEelProcess
    Assertions.assertThat(shellEelProcess.eelApi.descriptor).isEqualTo(eelApi.descriptor)
    TerminalSessionTestUtil.assumeTestableProcess(shellEelProcess)
    return sessionResult.session to shellEelProcess
  }

  private suspend fun echoShellVersion(session: TerminalSession, shellPath: EelPath) {
    when (PathUtil.getFileName(shellPath.toString())) {
      ShellNameUtil.BASH_NAME -> "BASH_VERSION"
      ShellNameUtil.ZSH_NAME -> "ZSH_VERSION"
      ShellNameUtil.FISH_NAME -> "FISH_VERSION"
      else -> null
    }?.let {
      session.getInputChannel().sendCommandToExecute("echo $$it", shellPath)
    }
  }

  /**
   * Async prompt spawns `(zsh)` and `(git)` child processes after command completion:
   * https://github.com/ohmyzsh/ohmyzsh?tab=readme-ov-file#async-git-prompt
   * We wait for these processes to terminate.
   */
  private suspend fun awaitNoCommandRunning(
    outputHandler: TestTerminalOutputHandler,
    lastCommand: String,
    shellProcess: ShellEelProcess,
  ) {
    awaitCondition(20.seconds) {
      val processSubtree = getProcessSubtree(shellProcess)
      val hasRunningCommands = outputHandler.session.hasRunningCommands() &&
                               !processSubtree.contains(ONLY_DEFUNCT_CHILD_PROCESSES_DETECTED)
      Assertions.assertThat(hasRunningCommands)
        .describedAs(
          "Command is running (last command: $lastCommand)\n" +
          processSubtree + "\n" +
          "--- OUTPUT ---\n" +
          outputHandler.getAllOutput()
        )
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

private suspend fun getProcessSubtree(shellEelProcess: ShellEelProcess): String {
  return when (val process = shellEelProcess.eelProcess) {
    is EelPosixProcess -> {
      try {
        getPosixProcessSubtree(process, shellEelProcess.eelApi as EelPosixApi)
      }
      catch (e: IllegalStateException) {
        "(failed to list descendant processes ($shellEelProcess): ${e.message})"
      }
    }
    is EelWindowsProcess -> "(no process subtree on Windows)"
  }
}

@Throws(IllegalStateException::class)
private suspend fun getPosixProcessSubtree(shellProcess: EelPosixProcess, eelApi: EelPosixApi): String = coroutineScope {
  val psProcess = try {
    buildPsCommand(eelApi)
      .env(eelApi.exec.environmentVariables().minimal().eelIt().await())
      .scope(this)
      .eelIt()
  }
  catch (e: ExecuteProcessException) {
    throw IllegalStateException("Cannot build `ps` command", e)
  }
  catch (e: EnvironmentVariablesException) {
    throw IllegalStateException("Cannot build `ps` command", e)
  }
  val result = withTimeoutOrNull(20.seconds) {
    psProcess.awaitProcessResult()
  } ?: throw IllegalStateException("Timed out when awaiting `ps` result")
  if (result.exitCode != 0) {
    throw IllegalStateException("`ps` terminated with exit code ${result.exitCode}, stderr: ${result.stderr.decodeToString()}")
  }
  buildProcessSubtree(shellProcess.pid, result.stdout.decodeToString())
}

@Throws(IllegalStateException::class)
private fun buildProcessSubtree(shellPid: EelApi.Pid, stdout: String): String {
  class ProcessInfo(val parentPid: Long, val pid: Long, val command: String, val line: String)
  val lines = stdout.trimEnd().lines()
  @Suppress("SpellCheckingInspection")
  val infos = lines.drop(1 /* drop "PPID PID COMMAND" header */).map { line ->
    val parts = line.trimStart().split(Regex("\\s+"), limit = 3)
    try {
      ProcessInfo(parts[0].toLong(), parts[1].toLong(), parts[2].trim(), line)
    }
    catch (e: Exception) {
      throw IllegalStateException("Cannot parse PPID/PID/COMMAND from `ps` output line: '$line'", e)
    }
  }
  val subtreePids = mutableSetOf<Long>()
  fun visitProcessTree(pid: Long) {
    if (subtreePids.add(pid)) {
      infos.filter { it.parentPid == pid }.forEach { visitProcessTree(it.pid) }
    }
  }
  visitProcessTree(shellPid.value)
  val subtree = infos.filter { subtreePids.contains(it.pid) }
  return buildList {
    add("Shell pid: ${shellPid.value}")
    add(lines.first())
    addAll(subtree.map { it.line })
    val defunctCount = subtree.filter { it.pid != shellPid.value }
      .count { it.command == "<defunct>" || it.command == "[python3] <defunct>" }
    if (defunctCount > 0 && subtree.size == defunctCount + 1) {
      add(ONLY_DEFUNCT_CHILD_PROCESSES_DETECTED)
    }
  }.joinToString(separator = "\n")
}

@Throws(IllegalStateException::class)
private suspend fun buildPsCommand(eelApi: EelPosixApi): EelExecApiHelpers.SpawnProcess {
  val psExe = eelApi.exec.findExeFilesInPath("ps").firstOrNull() ?: throw IllegalStateException("Cannot find `ps` executable")
  @Suppress("SpellCheckingInspection")
  return eelApi.exec.spawnProcess(psExe).args("-e", "-o", "ppid,pid,command")
}

private const val ONLY_DEFUNCT_CHILD_PROCESSES_DETECTED: String = "!!! Only <defunct> child processes detected !!!"

private val GREETING: String = ('A'..'Z').joinToString(separator = "")
