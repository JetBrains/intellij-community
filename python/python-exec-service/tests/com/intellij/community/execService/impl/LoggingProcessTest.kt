// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.community.execService.impl

import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.community.execService.impl.LoggingInputStream
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.ProcessOutputTopicSender
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.time.Instant

class LoggingProcessTest {
  @Test
  fun `logging process gets created with expected overrides`() {
    val (_, backingProcess, loggingProcess) = createLoggingProcess()

    assertEquals((loggingProcess.inputStream as LoggingInputStream).backingInputStream, backingProcess.outInputStream)
    assertEquals((loggingProcess.errorStream as LoggingInputStream).backingInputStream, backingProcess.errInputStream)
    assertEquals(loggingProcess.outputStream, backingProcess.inOutputStream)

    val exitCode = 1001

    backingProcess.exitFuture.complete(exitCode)

    assertEquals(exitCode, loggingProcess.waitFor())
    assertEquals(exitCode, loggingProcess.exitValue())
    assertEquals(loggingProcess, loggingProcess.onExit().join())
    assertEquals(true, loggingProcess.supportsNormalTermination())

    backingProcess.supportsNormalTermination.value = false

    assertEquals(false, loggingProcess.supportsNormalTermination())
  }

  @Test
  fun `process ids are incremented sequentially`() {
    val (_, _, process1) = createLoggingProcess()
    val (_, _, process2) = createLoggingProcess()
    val (_, _, process3) = createLoggingProcess()

    assert(process3.loggedProcess.id.value > process2.loggedProcess.id.value)
    assert(process2.loggedProcess.id.value > process1.loggedProcess.id.value)
  }

  @Test
  fun `process weight gets assigned correctly`() {
    val (_, _, processNull) = createLoggingProcess(weight = null)
    val (_, _, processLight) = createLoggingProcess(weight = ConcurrentProcessWeight.LIGHT)
    val (_, _, processMedium) = createLoggingProcess(weight = ConcurrentProcessWeight.MEDIUM)
    val (_, _, processHeavy) = createLoggingProcess(weight = ConcurrentProcessWeight.HEAVY)

    assertEquals(null, processNull.loggedProcess.weight)
    assertEquals(ProcessWeightDto.LIGHT, processLight.loggedProcess.weight)
    assertEquals(ProcessWeightDto.MEDIUM, processMedium.loggedProcess.weight)
    assertEquals(ProcessWeightDto.HEAVY, processHeavy.loggedProcess.weight)
  }

  @Test
  fun `process trace context gets assigned correctly`() = timeoutRunBlocking {
    val traceContext = TraceContext("test context")
    val (_, _, processNull) = createLoggingProcess(traceContext = null)
    val (_, _, processSet) = createLoggingProcess(traceContext = traceContext)

    assertEquals(null, processNull.loggedProcess.traceContextUuid)
    assertEquals(traceContext.uuid.toString(), processSet.loggedProcess.traceContextUuid?.value)
  }

  @Test
  fun `process pid gets assigned correctly`() {
    val pid = 100_000L
    val (_, _, processThrowing) = createLoggingProcess(pid = { throw UnsupportedOperationException() })
    val (_, _, processSet) = createLoggingProcess(pid = { pid })

    assertEquals(null, processThrowing.loggedProcess.pid)
    assertEquals(pid, processSet.loggedProcess.pid)
  }

  @Test
  fun `other process fields get assigned correctly`() {
    val startedAt = Instant.fromEpochMilliseconds(100_000_000)
    val cwd = "/some/path"
    val exe = Exe.OnTarget("/some/path/to/exe")
    val args = listOf("a", "b", "c")
    val env = mapOf("foo" to "bar", "baz" to "foo")
    val target = "someTarget"
    val (_, _, process) =
      createLoggingProcess(
        startedAt = startedAt,
        cwd = cwd,
        exe = exe,
        args = args,
        env = env,
        target = target,
      )

    assertEquals(startedAt, process.loggedProcess.startedAt)
    assertEquals(cwd, process.loggedProcess.cwd)
    assertEquals(
      ExecutableDto(
        path = exe.toString(),
        parts = exe.pathParts(),
      ),
      process.loggedProcess.exe
    )
    assertEquals(args, process.loggedProcess.args)
    assertEquals(env, process.loggedProcess.env)
    assertEquals(target, process.loggedProcess.target)
  }

  @Test
  fun `trace context hierarchy in new process event gets correctly calculated`() = timeoutRunBlocking {
    val grandParentContext = TraceContext("grandparent")
    val parentContext = TraceContext("parent", parentTraceContext = grandParentContext)
    val childContext = TraceContext("child", parentTraceContext = parentContext)
    val (events, _, _) = createLoggingProcess(traceContext = childContext)

    waitUntil { events.isNotEmpty() }

    val event = events[0]

    assertInstanceOf<ProcessOutputEventDto.NewProcess>(event)
    assertEquals(
      listOf(childContext.uuid.toString(), parentContext.uuid.toString(), grandParentContext.uuid.toString()),
      event.traceHierarchy.map { it.uuid.value }
    )
  }

  @Test
  fun `logging process events are received as expected`() = timeoutRunBlocking {
    val (events, backingProcess, loggingProcess) =
      createLoggingProcess(
        stdout = "out1\nout2\nout3\nout4\n",
        stderr = "err1\nerr2\nerr3\nerr4\n"
      )

    waitUntil { events.size == 1 }
    assertInstanceOf<ProcessOutputEventDto.NewProcess>(events[0])

    loggingProcess.inputStream.readAllBytes()

    waitUntil { events.size == 5 }

    for (i in 1..4) {
      val event = events[i]

      assertInstanceOf<ProcessOutputEventDto.NewOutputLine>(event)

      val line = event.outputLine

      assertEquals(OutputKindDto.OUT, line.kind)
      assertEquals("out$i", line.text)
    }

    loggingProcess.errorStream.readAllBytes()

    waitUntil { events.size == 9 }

    for (i in 5..8) {
      val event = events[i]

      assertInstanceOf<ProcessOutputEventDto.NewOutputLine>(event)

      val line = event.outputLine

      assertEquals(OutputKindDto.ERR, line.kind)
      assertEquals("err${i - 4}", line.text)
    }

    val exitCode = 100_000

    backingProcess.exitFuture.complete(exitCode)

    waitUntil { events.size == 10 }

    val event = events[9]

    assertInstanceOf<ProcessOutputEventDto.ProcessExit>(event)
    assertEquals(exitCode, event.exitValue)
    assertEquals(loggingProcess.loggedProcess.id, event.processId)
  }

  private class TestProcess(
    val outInputStream: InputStream,
    val errInputStream: InputStream,
    val pidGenerator: () -> Long,
  ) : Process() {
    val exitFuture: CompletableFuture<Int> = CompletableFuture()
    val inOutputStream: OutputStream = ByteArrayOutputStream()
    val supportsNormalTermination: MutableStateFlow<Boolean> = MutableStateFlow(true)

    override fun getOutputStream(): OutputStream =
      inOutputStream

    override fun getInputStream(): InputStream =
      outInputStream

    override fun getErrorStream(): InputStream =
      errInputStream

    override fun waitFor(): Int =
      exitFuture.join()

    override fun exitValue(): Int =
      exitFuture.join()

    override fun destroy() {
    }

    override fun onExit(): CompletableFuture<Process> =
      exitFuture.thenApply { this }

    override fun supportsNormalTermination(): Boolean =
      supportsNormalTermination.value

    override fun pid(): Long =
      this.pidGenerator()
  }

  private data class CreationResult(
    val events: List<ProcessOutputEventDto>,
    val backingProcess: TestProcess,
    val loggingProcess: LoggingProcess,
  )

  companion object {
    @OptIn(DelicateCoroutinesApi::class)
    private fun createLoggingProcess(
      stdout: String = "",
      stderr: String = "",
      pid: () -> Long = { 0 },
      weight: ConcurrentProcessWeight? = null,
      traceContext: TraceContext? = null,
      startedAt: Instant = Instant.fromEpochMilliseconds(0),
      cwd: String? = "/some/path",
      exe: Exe = Exe.OnTarget("/some/target/path"),
      args: List<String> = listOf(),
      env: Map<String, String> = mapOf(),
      target: String = "target",
    ): CreationResult {
      val events = mutableListOf<ProcessOutputEventDto>()
      val backingProcess =
        TestProcess(
          stdout.byteInputStream(),
          stderr.byteInputStream(),
          pid,
        )
      val loggingProcess =
        LoggingProcess(
          backingProcess,
          weight,
          traceContext,
          startedAt,
          cwd,
          exe,
          args,
          env,
          target,
          object : ProcessOutputTopicSender {
            override fun sendNewProcessEvent(loggedProcessDto: LoggedProcessDto, traceHierarchy: List<TraceContextDto>) {
              events += ProcessOutputEventDto.NewProcess(loggedProcessDto, traceHierarchy)
            }

            override fun sendNewOutputLineEvent(processId: ProcessId, outputLine: OutputLineDto) {
              events += ProcessOutputEventDto.NewOutputLine(processId, outputLine)
            }

            override fun sendProcessExitEvent(processId: ProcessId, exitedAt: Instant, exitValue: Int) {
              events += ProcessOutputEventDto.ProcessExit(processId, exitedAt, exitValue)
            }

            override fun sendExecErrorEvent(execErrorDto: ExecErrorDto) {
              unexpectedMethodCalledException()
            }

            override fun sendOpenToolWindowByTraceUuidEvent(uuid: UUID, openIfNotFound: Boolean) {
              unexpectedMethodCalledException()
            }

            override fun sendOpenToolWindowByTraceUuidEvent(uuid: String, openIfNotFound: Boolean) {
              unexpectedMethodCalledException()
            }

            fun unexpectedMethodCalledException() {
              throw IllegalStateException("unexpected method called")
            }
          },
          GlobalScope,
        )

      return CreationResult(events, backingProcess, loggingProcess)
    }
  }
}
