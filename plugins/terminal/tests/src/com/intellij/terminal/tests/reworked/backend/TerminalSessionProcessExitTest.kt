// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ObservableTtyConnector
import com.intellij.terminal.frontend.session.createStandardStateAwareTerminalSession
import com.intellij.terminal.frontend.session.createTerminalSession
import com.intellij.terminal.frontend.session.startStandardTerminalSession
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil.ENTER_BYTES
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil.awaitOutputEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@TestApplication
internal class TerminalSessionProcessExitTest {
  @ParameterizedTest
  @MethodSource("exitCodes")
  fun `process exit code remains available after EOF`(
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
    exitCode: Int,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    val process = ControlledProcess()
    val connector = ControlledProcessConnector(process)
    try {
      val session = createSession(connector, emulator, wrapped, scope)
      assertExitAfterEof(session, connector, process, scope, exitCode)
    }
    finally {
      process.exit.complete(1)
      scope.cancel()
    }
  }

  @ParameterizedTest
  @MethodSource("sessions")
  fun `caller cancellation leaves the process and another wait active`(
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    val process = ControlledProcess()
    try {
      val session = createSession(ControlledProcessConnector(process), emulator, wrapped, scope)
      val cancelled = async { session.awaitProcessExitCode() }
      val remaining = async { session.awaitProcessExitCode() }
      process.waitStarted.receive()
      cancelled.cancelAndJoin()

      assertThat(cancelled.isCancelled).isTrue()
      assertThat(process.isAlive).isTrue()
      assertThat(process.destroyRequested.isCompleted).isFalse()
      assertThat(scope.coroutineContext.job.isActive).isTrue()
      assertThat(remaining.isCompleted).isFalse()

      process.exit.complete(23)
      assertThat(remaining.await()).isEqualTo(23)
      assertThat(session.awaitProcessExitCode()).isEqualTo(23)
    }
    finally {
      process.exit.complete(1)
      scope.cancel()
    }
  }

  @ParameterizedTest
  @MethodSource("sessions")
  fun `a connector without a process has no exit code`(
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    try {
      val (session, _) = TerminalSessionTestUtil.createLoopbackTerminalSession(
        project = projectFixture.get(),
        coroutineScope = scope,
        emulatorType = emulator,
        isLowLevelSession = !wrapped,
      )
      assertThat(session.awaitProcessExitCode()).isNull()
    }
    finally {
      scope.cancel()
    }
  }

  @ParameterizedTest
  @MethodSource("shellExits")
  fun `shell exit returns its actual code`(
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
    exitCode: Int,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    try {
      val session = startShellSession(emulator, wrapped, scope)
      session.awaitOutputEvent(TerminalPromptFinishedEvent)
      val terminated = launch(start = CoroutineStart.UNDISPATCHED) { session.awaitOutputEvent(TerminalSessionTerminatedEvent) }
      session.getInputChannel().send(TerminalWriteBytesEvent("exit $exitCode".toByteArray() + ENTER_BYTES))
      assertThat(session.awaitProcessExitCode()).isEqualTo(exitCode)
      terminated.join()
      assertThat(session.awaitProcessExitCode()).isEqualTo(exitCode)
    }
    finally {
      scope.cancel()
    }
  }

  @ParameterizedTest
  @EnumSource(TerminalEmulatorType::class)
  fun `startup failure produces no session`(emulator: TerminalEmulatorType, @TempDir directory: Path): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    try {
      val options = ShellStartupOptions.Builder()
        .shellCommand(listOf(directory.resolve("missing-executable").toString()))
        .workingDirectory(directory.toString())
        .processType(TerminalProcessType.NON_SHELL)
        .emulatorType(emulator)
        .build()
      val failure = assertThrows<Exception> { startStandardTerminalSession(projectFixture.get(), options, scope) }
      assertThat(failure).hasMessageContaining("missing-executable")
    }
    finally {
      scope.cancel()
    }
  }

  private suspend fun assertExitAfterEof(
    session: TerminalSession,
    connector: ControlledProcessConnector,
    process: ControlledProcess,
    scope: CoroutineScope,
    exitCode: Int,
  ) = coroutineScope {
    val output = launch(start = CoroutineStart.UNDISPATCHED) { session.getOutputFlow().collect {} }
    val first = async { session.awaitProcessExitCode() }
    val second = async { session.awaitProcessExitCode() }
    process.waitStarted.receive()
    connector.output.close()
    scope.coroutineContext.job.join()
    assertThat(process.isAlive).isTrue()
    assertThat(first.isCompleted).isFalse()
    assertThat(second.isCompleted).isFalse()
    process.exit.complete(exitCode)
    assertThat(first.await()).isEqualTo(exitCode)
    assertThat(second.await()).isEqualTo(exitCode)
    assertThat(session.awaitProcessExitCode()).isEqualTo(exitCode)
    output.cancelAndJoin()
  }

  private fun startShellSession(emulator: TerminalEmulatorType, wrapped: Boolean, scope: CoroutineScope): TerminalSession {
    val shellPath = TerminalSessionTestUtil.getShellPaths().first()
    val command = TerminalSessionTestUtil.createShellCommand(shellPath.toString())
    TerminalSessionTestUtil.assumeCommandBlockShellIntegration(command)
    val options = ShellStartupOptions.Builder()
      .shellCommand(command)
      .workingDirectory(System.getProperty("user.home"))
      .emulatorType(emulator)
      .build()
    return TerminalSessionTestUtil.startTestTerminalSession(projectFixture.get(), options, !wrapped, scope).session
  }

  @ParameterizedTest
  @MethodSource("sessions")
  fun `process observation failure propagates`(
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalSession", Dispatchers.Default)
    val process = ControlledProcess()
    try {
      val session = createSession(ControlledProcessConnector(process), emulator, wrapped, scope)
      val failure = IOException("Process exit is unavailable")
      process.exit.completeExceptionally(failure)

      val thrown = assertThrows<ExecutionException> { session.awaitProcessExitCode() }
      assertThat(thrown).hasRootCause(failure)
    }
    finally {
      process.exit.complete(1)
      scope.cancel()
    }
  }

  private fun createSession(
    connector: TtyConnector,
    emulator: TerminalEmulatorType,
    wrapped: Boolean,
    scope: CoroutineScope,
  ): TerminalSession {
    val options = ShellStartupOptions.Builder()
      .initialTermSize(TermSize(80, 24))
      .setFinalWorkingDirectoryEelPath(Path.of(System.getProperty("user.home")).asEelPath())
      .processType(TerminalProcessType.NON_SHELL)
      .emulatorType(emulator)
      .build()
    val delegate = createTerminalSession(
      null, ObservableTtyConnector(connector), options, JBTerminalSystemSettingsProvider(),
      if (wrapped) scope.childScope("original session") else scope,
    )
    if (!wrapped) return delegate
    val startupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("controlled-process"),
      workingDirectory = System.getProperty("user.home"),
      envVariables = emptyMap(),
      processType = TerminalProcessType.NON_SHELL,
      pid = null,
    )
    return createStandardStateAwareTerminalSession(delegate, startupOptions, scope)
  }

  companion object {
    private val projectFixture = projectFixture()

    @JvmStatic
    fun sessions(): List<Arguments> = TerminalEmulatorType.entries.flatMap { emulator ->
      listOf(Arguments.of(emulator, false), Arguments.of(emulator, true))
    }

    @JvmStatic
    fun exitCodes(): List<Arguments> = TerminalEmulatorType.entries.flatMap { emulator ->
      listOf(false, true).flatMap { wrapped ->
        listOf(0, 17, -1, 143).map { exitCode -> Arguments.of(emulator, wrapped, exitCode) }
      }
    }

    @JvmStatic
    fun shellExits(): List<Arguments> = TerminalEmulatorType.entries.flatMap { emulator ->
      listOf(false, true).flatMap { wrapped ->
        listOf(0, 17).map { exitCode -> Arguments.of(emulator, wrapped, exitCode) }
      }
    }
  }
}

private class ControlledProcess : Process() {
  val exit = CompletableFuture<Int>()
  val waitStarted = Channel<Unit>(Channel.UNLIMITED)
  val destroyRequested = CompletableDeferred<Unit>()

  override fun getInputStream(): ByteArrayInputStream = ByteArrayInputStream(byteArrayOf())
  override fun getErrorStream(): ByteArrayInputStream = ByteArrayInputStream(byteArrayOf())
  override fun getOutputStream(): ByteArrayOutputStream = ByteArrayOutputStream()

  override fun waitFor(): Int {
    waitStarted.trySend(Unit)
    return exit.get()
  }

  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    waitStarted.trySend(Unit)
    return try {
      exit.get(timeout, unit)
      true
    }
    catch (_: TimeoutException) {
      false
    }
  }

  override fun exitValue(): Int = if (exit.isDone) exit.get() else throw IllegalThreadStateException("Process is running")
  override fun isAlive(): Boolean = !exit.isDone
  override fun destroy() {
    destroyRequested.complete(Unit)
  }
}

private class ControlledProcessConnector(process: ControlledProcess) : ProcessTtyConnector(process, Charsets.UTF_8) {
  val output = LoopbackTtyConnector()

  override fun read(buf: CharArray, offset: Int, length: Int): Int = output.read(buf, offset, length)
  override fun ready(): Boolean = output.ready()
  override fun isConnected(): Boolean = output.isConnected
  override fun resize(termSize: TermSize) {}
  override fun getName(): String = "controlled-process"

  override fun waitFor(): Int = 0

  override fun close() {
    output.close()
    process.destroy()
  }
}
