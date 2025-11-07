// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.community.execService.impl.LoggedProcessExe
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class LoggingTest {
  @Nested
  inner class LoggedProcessTest {
    @Test
    fun `loggedProcess id should increment with each instantiation`() {
      val process1 = process("process1")
      val process2 = process("process2")
      val process3 = process("process2")

      assert(process1.id + 1 == process2.id)
      assert(process2.id + 1 == process3.id)
    }

    @Test
    fun `commandString is constructed as expected`() {
      val process1 = process("/usr/bin/uv", "install", "requests")

      assertEquals("/usr/bin/uv install requests", process1.commandString)
    }

    @Test
    fun `shortenedCommandString is constructed as expected from multiple segments`() {
      val process1 = process("/usr/bin/uv", "install", "requests")

      assertEquals("uv install requests", process1.shortenedCommandString)
    }

    @Test
    fun `shortenedCommandString is constructed as expected from single segment`() {
      val process1 = process("uv", "install", "requests")

      assertEquals("uv install requests", process1.shortenedCommandString)
    }
  }

  @TestApplication
  @Nested
  inner class LoggingProcessTest {
    @Test
    fun `logged process gets created correctly`() = timeoutRunBlocking(timeout = 1.minutes) {
      val traceContext = TraceContext("some trace")
      val loggingProcess = fakeLoggingProcess(
        stdout = "stdout text",
        stderr = "stderr text",
        exitValue = 10,
        pid = 100,
        traceContext = traceContext,
        startedAt = Instant.fromEpochSeconds(100),
        cwd = "/some/cwd",
        pathToExe = "/usr/bin/exe",
        args = listOf("foo", "bar"),
        env = mapOf("foo" to "bar")
      )

      val loggedProcess = loggingProcess.loggedProcess
      val stdout = loggingProcess.inputStream.readAllBytes().toString(charset = Charsets.UTF_8)
      val stderr = loggingProcess.errorStream.readAllBytes().toString(charset = Charsets.UTF_8)

      assert(traceContext == loggedProcess.traceContext)
      assert(100L == loggedProcess.pid)
      assert(Instant.fromEpochSeconds(100) == loggedProcess.startedAt)
      assert("/some/cwd" == loggedProcess.cwd)
      assert(LoggedProcessExe(path = "/usr/bin/exe", listOf("usr", "bin", "exe")) == loggedProcess.exe)
      assert(listOf("foo", "bar") == loggedProcess.args)
      assert(mapOf("foo" to "bar") == loggedProcess.env)
      assert(stdout == "stdout text")
      assert(stderr == "stderr text")

      loggingProcess.destroy()
    }

    @Test
    fun `lines get properly collected from out and err`() = timeoutRunBlocking(timeout = 1.minutes) {
      val loggingProcess = fakeLoggingProcess(
        "outline1\noutline2\noutline3",
        "errline1\nerrline2\nerrline3"
      )
      val loggedProcess = loggingProcess.loggedProcess

      assert(loggedProcess.lines.replayCache.isEmpty())

      loggingProcess.inputStream.readAllBytes()
      loggingProcess.errorStream.readAllBytes()

      waitUntil { loggedProcess.lines.replayCache.size == 6 }

      (1..3).forEach {
        assert(loggedProcess.lines.replayCache[it - 1].text == "outline$it")
        assert(loggedProcess.lines.replayCache[it - 1].kind == LoggedProcessLine.Kind.OUT)
      }

      (4..6).forEach {
        assert(loggedProcess.lines.replayCache[it - 1].text == "errline${it - 3}")
        assert(loggedProcess.lines.replayCache[it - 1].kind == LoggedProcessLine.Kind.ERR)
      }

      loggingProcess.destroy()
    }

    @Test
    fun `exit info gets properly populated`() = timeoutRunBlocking(timeout = 1.minutes) {
      val now = Clock.System.now()
      val loggingProcess = fakeLoggingProcess(
        exitValue = 30
      )
      val loggedProcess = loggingProcess.loggedProcess

      loggingProcess.destroy()

      waitUntil { loggedProcess.exitInfo.value != null }

      assert(loggedProcess.exitInfo.value!!.exitValue == 30)
      assert(loggedProcess.exitInfo.value!!.exitedAt >= now)
    }

    @Disabled
    @Test
    fun `old lines are evicted when the line limit is reached`() = timeoutRunBlocking(timeout = 1.minutes) {
      val loggingProcess = fakeLoggingProcess(
        stdout = buildString {
          repeat(LoggingLimits.MAX_LINES + 2) {
            appendLine("line$it")
          }
        },
        stderr = ""
      )
      val loggedProcess = loggingProcess.loggedProcess

      loggingProcess.inputStream.readAllBytes()
      loggingProcess.errorStream.readAllBytes()

      loggingProcess.destroy()

      waitUntil { loggedProcess.lines.replayCache.last().text == "line${LoggingLimits.MAX_LINES + 1}" }

      assert(loggedProcess.lines.replayCache.size == LoggingLimits.MAX_LINES)
      assert(loggedProcess.lines.replayCache[0].text == "line2")
    }

    // todo: add limits test
  }

  companion object {
    fun process(vararg command: String) =
      LoggedProcess(
        traceContext = null,
        pid = 123,
        startedAt = Clock.System.now(),
        cwd = null,
        exe = LoggedProcessExe(
          path = command.first(),
          parts = command.first().split(Regex("[/\\\\]+"))
        ),
        args = command.drop(1),
        env = mapOf(),
        lines = MutableSharedFlow(),
        exitInfo = MutableStateFlow(null),
      )

    fun fakeLoggingProcess(
      stdout: String = "stdout",
      stderr: String = "stderr",
      exitValue: Int = 0,
      pid: Long = 0,
      traceContext: TraceContext? = null,
      startedAt: Instant = Instant.fromEpochSeconds(0),
      cwd: String? = "/some/cwd",
      pathToExe: String = "/usr/bin/exe",
      args: List<String> = listOf("foo", "bar"),
      env: Map<String, String> = mapOf("foo" to "bar"),
    ) =
      LoggingProcess(
        object : Process() {
          val stdoutStream = ByteArrayInputStream(stdout.toByteArray())
          val stderrStream = ByteArrayInputStream(stderr.toByteArray())
          val stdinStream = ByteArrayOutputStream()
          val destroyFuture = CompletableFuture<Int>()

          override fun getOutputStream(): OutputStream =
            stdinStream

          override fun getInputStream(): InputStream =
            stdoutStream

          override fun getErrorStream(): InputStream =
            stderrStream

          override fun waitFor(): Int {
            destroyFuture.get()
            return exitValue
          }

          override fun exitValue(): Int {
            return exitValue
          }

          override fun destroy() {
            destroyFuture.complete(10)
          }

          override fun pid(): Long =
            pid
        },
        traceContext,
        startedAt,
        cwd,
        Exe.fromString(pathToExe),
        args,
        env
      )
  }
}

