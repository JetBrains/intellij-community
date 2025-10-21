// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.readLineAsync
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

internal object LoggingLimits {
  const val MAX_LINE_SIZE = 16_384
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

    val outCollector = service.scope.launch {
      collectOutputLines(stdoutStream.inputStream, linesFlow, LoggedProcessLine.Kind.OUT)
    }

    val errCollector = service.scope.launch {
      collectOutputLines(stderrStream.inputStream, linesFlow, LoggedProcessLine.Kind.ERR)
    }

    service.scope.launch {
      service.processesInternal.emit(loggedProcess)
      withContext(Dispatchers.IO) {
        waitFor()
      }
      exitInfoFlow.value = LoggedProcessExitInfo(
        exitedAt = Clock.System.now(),
        exitValue = exitValue(),
      )

      outCollector.cancel()
      errCollector.cancel()
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
  private val outputStream = PipedOutputStream()
  val inputStream: InputStream = PipedInputStream(outputStream)

  override fun read(): Int {
    val byte = try {
      backingInputStream.read()
    }
    catch (e: IOException) {
      outputStream.close()

      // ugly hack; but the Process' `.destroy` methods abruptly close
      // the stream, making all pending readers throw an exception.
      // we can handle this case as legal here
      if (e.message == "Stream closed") {
        return -1
      }

      throw e
    }

    try {
      if (byte == -1) {
        outputStream.close()
      }
      else {
        outputStream.write(byte)
      }
    }
    catch (_: IOException) {
      // pipe might be closed, simply ignore it in this case
    }

    return byte
  }
}

private suspend fun collectOutputLines(
  inputStream: InputStream,
  linesFlow: MutableSharedFlow<LoggedProcessLine>,
  kind: LoggedProcessLine.Kind,
) {
  val reader = BufferedReader(InputStreamReader(inputStream))
  var line: String? = null

  while (reader.readLineAsync()?.also { line = it } != null) {
    linesFlow.emit(LoggedProcessLine(
      text = line!!.substring(0, line.length.coerceAtMost(LoggingLimits.MAX_LINE_SIZE)),
      kind = kind,
    ))
  }
}
