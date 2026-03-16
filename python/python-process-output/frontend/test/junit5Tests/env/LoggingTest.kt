// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class LoggingTest {
    @Nested
    inner class LoggedProcessTest {
        @Test
        fun `commandString is constructed as expected`() {
            val process1 = process("/usr/bin/uv", "install", "requests")

            assertEquals("/usr/bin/uv install requests", process1.data.commandString)
        }

        @Test
        fun `shortenedCommandString is constructed as expected from multiple segments`() {
            val process1 = process("/usr/bin/uv", "install", "requests")

            assertEquals("uv install requests", process1.data.shortenedCommandString)
        }

        @Test
        fun `shortenedCommandString is constructed as expected from single segment`() {
            val process1 = process("uv", "install", "requests")

            assertEquals("uv install requests", process1.data.shortenedCommandString)
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
                env = mapOf("foo" to "bar"),
            )

            val loggedProcess = loggingProcess.loggedProcess
            val stdout =
                loggingProcess.inputStream.readAllBytes().toString(charset = Charsets.UTF_8)
            val stderr =
                loggingProcess.errorStream.readAllBytes().toString(charset = Charsets.UTF_8)

            assert(traceContext.uuid.toString() == loggedProcess.traceContextUuid?.uuid)
            assert(100L == loggedProcess.pid)
            assert(Instant.fromEpochSeconds(100) == loggedProcess.startedAt)
            assert("/some/cwd" == loggedProcess.cwd)
            assert(
                ExecutableDto(
                    path = "/usr/bin/exe",
                    listOf("usr", "bin", "exe"),
                ) == loggedProcess.exe,
            )
            assert(listOf("foo", "bar") == loggedProcess.args)
            assert(mapOf("foo" to "bar") == loggedProcess.env)
            assert(stdout == "stdout text")
            assert(stderr == "stderr text")

            loggingProcess.destroy()
        }
    }

    companion object {
        val nextId = AtomicInteger()

        fun process(vararg command: String) =
            LoggedProcess(
                data = LoggedProcessDto(
                    weight = null,
                    traceContextUuid = null,
                    pid = 123,
                    startedAt = Clock.System.now(),
                    cwd = null,
                    exe = ExecutableDto(
                        path = command.first(),
                        parts = command.first().split(Regex("[/\\\\]+")),
                    ),
                    args = command.drop(1),
                    env = mapOf(),
                    target = "Local",
                    id = nextId.getAndAdd(1),
                ),
                lines = SnapshotStateList(),
                status = MutableStateFlow(ProcessStatus.Running),
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
                null,
                traceContext,
                startedAt,
                cwd,
                Exe.fromString(pathToExe),
                args,
                env,
                "Local",
            )
    }
}

