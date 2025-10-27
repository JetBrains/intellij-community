// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.google.common.io.ByteStreams
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.awaitExit
import com.intellij.util.io.readLineAsync
import com.intellij.util.io.toByteArray
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

internal object LoggingLimits {
  /**
   * The maximum buffer size of a LoggingProcess
   */
  const val MAX_OUTPUT_SIZE = 10_000_000
  const val MAX_LINES = 1024
}

@ApiStatus.Internal
data class LoggedProcess(
  val traceContext: TraceContext?,
  val pid: Long?,
  val startedAt: Instant,
  val cwd: String?,
  val exe: LoggedProcessExe,
  val args: List<String>,
  val env: Map<String, String>,
  val lines: SharedFlow<LoggedProcessLine>,
  val exitInfo: MutableStateFlow<LoggedProcessExitInfo?>,
) {
  val id: Int = nextId.getAndAdd(1)

  val commandString: String
    get() = commandFromSegments(listOf(exe.path) + args)

  /**
   * Command string with the full path of the exe trimmed only to the latest segments. E.g., `/usr/bin/uv` -> `uv`.
   */
  val shortenedCommandString: String
    get() = commandFromSegments(listOf(exe.parts.last()) + args)

  companion object {
    private val nextId: AtomicInteger = AtomicInteger(0)

    private fun commandFromSegments(segments: List<String>) =
      segments.joinToString(" ")
  }
}

@ApiStatus.Internal
data class LoggedProcessExe(
  val path: String,
  val parts: List<String>,
)

@ApiStatus.Internal
data class LoggedProcessExitInfo(
  val exitedAt: Instant,
  val exitValue: Int,
  val additionalMessageToUser: @Nls String? = null,
)

@ApiStatus.Internal
data class LoggedProcessLine(
  val text: String,
  val kind: Kind,
) {
  enum class Kind {
    OUT,
    ERR
  }
}

@ApiStatus.Internal
@Service
class ExecLoggerService(val scope: CoroutineScope) {
  internal val processesInternal = MutableSharedFlow<LoggedProcess>()
  val processes: Flow<LoggedProcess> = processesInternal.asSharedFlow()
}

@ApiStatus.Internal
class LoggingProcess(
  private val backingProcess: Process,
  traceContext: TraceContext?,
  startedAt: Instant,
  cwd: String?,
  exe: Exe,
  args: List<String>,
  env: Map<String, String>,
) : Process() {
  val loggedProcess: LoggedProcess

  private val stdoutStream = LoggingInputStream(backingProcess.inputStream)
  private val stderrStream = LoggingInputStream(backingProcess.errorStream)

  init {
    val service = ApplicationManager.getApplication().service<ExecLoggerService>()
    val linesFlow = MutableSharedFlow<LoggedProcessLine>(replay = LoggingLimits.MAX_LINES)
    val exitInfoFlow = MutableStateFlow<LoggedProcessExitInfo?>(null)

    loggedProcess =
      LoggedProcess(
        traceContext,
        try {
          backingProcess.pid()
        }
        catch (_: UnsupportedOperationException) {
          null
        },
        startedAt,
        cwd,
        LoggedProcessExe(
          path = exe.toString(),
          parts = exe.pathParts(),
        ),
        args,
        env,
        linesFlow,
        exitInfoFlow,
      )

    service.scope.launch {
      service.processesInternal.emit(loggedProcess)

      awaitExit()

      val stdoutReader = BufferedReader(InputStreamReader(ByteArrayInputStream(stdoutStream.byteArray)))
      val stderrReader = BufferedReader(InputStreamReader(ByteArrayInputStream(stderrStream.byteArray)))

      collectOutputLines(stdoutReader, linesFlow, LoggedProcessLine.Kind.OUT)
      collectOutputLines(stderrReader, linesFlow, LoggedProcessLine.Kind.ERR)

      exitInfoFlow.value = LoggedProcessExitInfo(
        exitedAt = Clock.System.now(),
        exitValue = exitValue(),
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
}

private class LoggingInputStream(
  private val backingInputStream: InputStream,
) : InputStream() {
  private val bytes = ByteStreams.newDataOutput()
  private var tail = 0
  private var closed = AtomicBoolean(false)

  val byteArray
    get() = bytes.toByteArray()

  override fun read(): Int {
    if (closed.get()) {
      return -1
    }

    val byte = backingInputStream.read()

    if (tail < LoggingLimits.MAX_OUTPUT_SIZE && byte != -1) {
      bytes.write(byte)
      tail += 1
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

    if (finalLen != -1) {
      val truncatedLen = if (tail + finalLen > LoggingLimits.MAX_OUTPUT_SIZE) {
        LoggingLimits.MAX_OUTPUT_SIZE - tail
      }
      else {
        finalLen
      }

      if (truncatedLen > 0) {
        bytes.write(b, off, truncatedLen)
        tail += truncatedLen
      }
    }

    return finalLen
  }

  override fun close() {
    closed.set(true)
    super.close()
  }
}

private suspend fun collectOutputLines(
  reader: BufferedReader,
  linesFlow: MutableSharedFlow<LoggedProcessLine>,
  kind: LoggedProcessLine.Kind,
) {
  var line: String? = null

  while (reader.readLineAsync()?.also { line = it } != null) {
    linesFlow.emit(LoggedProcessLine(
      text = line!!,
      kind = kind,
    ))
  }
}
