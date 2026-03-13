// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.google.common.io.ByteStreams
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.common.sendProcessOutputTopicEvent
import com.intellij.util.io.awaitExit
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

@ApiStatus.Internal
object LoggingLimits {
  /**
   * The maximum buffer size of a LoggingProcess
   */
  const val MAX_OUTPUT_SIZE = 100_000
}

@ApiStatus.Internal
class LoggingProcess(
  private val backingProcess: Process,
  weight: ConcurrentProcessWeight?,
  traceContext: TraceContext?,
  startedAt: Instant,
  cwd: String?,
  exe: Exe,
  args: List<String>,
  env: Map<String, String>,
  target: String,
) : Process() {
  val loggedProcess: LoggedProcessDto =
    LoggedProcessDto(
      weight =
        when (weight) {
          ConcurrentProcessWeight.LIGHT -> ProcessWeightDto.LIGHT
          ConcurrentProcessWeight.MEDIUM -> ProcessWeightDto.MEDIUM
          ConcurrentProcessWeight.HEAVY -> ProcessWeightDto.HEAVY
          null -> null
        },
      traceContextUuid =
        traceContext?.let {
          TraceContextUuid(it.uuid.toString())
        },
      pid =
        try {
          backingProcess.pid()
        }
        catch (_: UnsupportedOperationException) {
          null
        },
      startedAt = startedAt,
      cwd = cwd,
      exe =
        ExecutableDto(
          path = exe.toString(),
          parts = exe.pathParts(),
        ),
      args = args,
      env = env,
      target = target,
      id = nextId.getAndAdd(1),
    )
  private val lineCounter = AtomicInteger(0)

  private val stdoutStream = LoggingInputStream(loggedProcess.id, backingProcess.inputStream, OutputKindDto.OUT, lineCounter)
  private val stderrStream = LoggingInputStream(loggedProcess.id, backingProcess.errorStream, OutputKindDto.ERR, lineCounter)

  init {
    ApplicationManager.getApplication().service<LoggingService>().scope.launch {
      val traceHierarchy = mutableListOf<TraceContextDto>()
      var currentTraceContext = traceContext

      while (currentTraceContext != null) {
        traceHierarchy += currentTraceContext.toDto()
        currentTraceContext = currentTraceContext.parentTraceContext
      }

      sendProcessOutputTopicEvent(
        ProcessOutputEventDto.NewProcess(loggedProcess, traceHierarchy)
      )

      awaitExit()

      sendProcessOutputTopicEvent(
        ProcessOutputEventDto.ProcessExit(
          processId = loggedProcess.id,
          exitedAt = Clock.System.now(),
          exitValue = exitValue(),
        )
      )
    }
  }

  override fun getOutputStream(): OutputStream =
    backingProcess.outputStream

  override fun getInputStream(): InputStream =
    stdoutStream

  override fun getErrorStream(): InputStream =
    stderrStream

  override fun waitFor(): Int {
    return backingProcess.waitFor()
  }

  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    return backingProcess.waitFor(timeout, unit)
  }

  override fun exitValue(): Int =
    backingProcess.exitValue()

  override fun destroy(): Unit =
    backingProcess.destroy()

  override fun destroyForcibly(): Process? =
    backingProcess.destroyForcibly()

  override fun toHandle(): ProcessHandle? =
    backingProcess.toHandle()

  override fun supportsNormalTermination(): Boolean =
    backingProcess.supportsNormalTermination()

  companion object {
    private val nextId: AtomicInteger = AtomicInteger(0)
  }
}

private class LoggingInputStream(
  private val processId: Int,
  private val backingInputStream: InputStream,
  private val kind: OutputKindDto,
  private val lineCounter: AtomicInteger,
) : InputStream() {
  private var closed = AtomicBoolean(false)
  private var outputSize = 0
  private var reachedEnd = false
  private var currentLineBytes = ByteStreams.newDataOutput()

  override fun read(): Int {
    if (closed.get()) {
      return -1
    }

    val byte = backingInputStream.read()

    checkForStreamEnd(byte)

    if (!reachedEnd) {
      processChar(byte)
      outputSize += 1
    }

    return byte
  }

  /**
   * Chunked read. The read bytes are also logged into the corresponding byte stream. If limit of [LoggingLimits.MAX_OUTPUT_SIZE] is
   * reached, then the logged bytes are truncated.
   */
  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (closed.get()) {
      return -1
    }

    val finalLen = backingInputStream.read(b, off, len)

    checkForStreamEnd(finalLen)

    if (!reachedEnd) {
      val truncatedLen = if (outputSize + finalLen > LoggingLimits.MAX_OUTPUT_SIZE) {
        LoggingLimits.MAX_OUTPUT_SIZE - outputSize
      }
      else {
        finalLen
      }

      for (index in off..<off + truncatedLen) {
        processChar(b[index].toInt())
      }
      outputSize += truncatedLen
    }

    return finalLen
  }

  override fun close() {
    closed.set(true)
    finalizeLastLine()
    super.close()
  }

  private fun checkForStreamEnd(char: Int) {
    if (!reachedEnd && (outputSize >= LoggingLimits.MAX_OUTPUT_SIZE || char == -1)) {
      reachedEnd = true
      finalizeLastLine()
    }
  }

  private fun processChar(char: Int) {
    if (char == -1) {
      return
    }

    when (char.toChar()) {
      '\r' -> {
        // ignore
      }
      '\n' -> {
        finalizeLine(currentLineBytes.toByteArray())
      }
      else -> {
        currentLineBytes.write(char)
      }
    }
  }

  private fun finalizeLine(bytes: ByteArray) {
    val line = String(bytes)

    sendProcessOutputTopicEvent(
      ProcessOutputEventDto.NewOutputLine(
        processId = processId,
        OutputLineDto(
          kind = kind,
          text = line,
          lineNo = lineCounter.getAndAdd(1),
        )
      )
    )

    currentLineBytes = ByteStreams.newDataOutput()
  }

  private fun finalizeLastLine() {
    val bytes = currentLineBytes.toByteArray()

    if (bytes.size > 0) {
      finalizeLine(bytes)
    }
  }
}

private fun TraceContext.toDto(): TraceContextDto =
  TraceContextDto(
    title = title,
    timestamp = timestamp,
    uuid = TraceContextUuid(uuid.toString()),
    kind =
      when (this) {
        NON_INTERACTIVE_ROOT_TRACE_CONTEXT -> TraceContextKind.NON_INTERACTIVE
        else -> TraceContextKind.INTERACTIVE
      },
    parentUuid =
      parentTraceContext?.let {
        TraceContextUuid(it.uuid.toString())
      }
  )

@Service
private class LoggingService(val scope: CoroutineScope)
