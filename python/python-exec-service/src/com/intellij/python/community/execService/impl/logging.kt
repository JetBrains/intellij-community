// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.google.common.io.ByteStreams
import com.intellij.execution.process.ProcessTreeKiller
import com.intellij.execution.process.SelfKiller
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputTopicSender
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

internal object LoggingLimits {
  /**
   * The maximum buffer size of a LoggingProcess
   */
  const val MAX_OUTPUT_SIZE: Int = 100_000
}

private data class LoggingConfig(
  val backingProcess: Process,
  val weight: ConcurrentProcessWeight?,
  val traceContext: TraceContext?,
  val startedAt: Instant,
  val cwd: String?,
  val exe: Exe,
  val args: List<String>,
  val env: Map<String, String>,
  val target: String,
  val topicSender: ProcessOutputTopicSender,
  val coroutineScope: CoroutineScope,
)

@PyInternalExecApi
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface LoggingProcess {
  @get:TestOnly
  val loggedProcess: LoggedProcessDto
}

internal sealed class LoggingProcessImpl(private val cfg: LoggingConfig) : Process(), LoggingProcess {
  override val loggedProcess: LoggedProcessDto = with(cfg) {
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
      id = ProcessId(nextId.getAndAdd(1)),
    )
  }

  private val stdoutStream = with(cfg) {
    LoggingInputStream(
      loggedProcess.id,
      backingProcess.inputStream,
      OutputKindDto.OUT,
      topicSender,
    )
  }
  private val stderrStream = with(cfg) {
    LoggingInputStream(
      loggedProcess.id,
      backingProcess.errorStream,
      OutputKindDto.ERR,
      topicSender,
    )
  }

  init {
    with(cfg) {
      coroutineScope.launch {
        val traceHierarchy = mutableListOf<TraceContextDto>()
        var currentTraceContext = traceContext

        while (currentTraceContext != null) {
          traceHierarchy += currentTraceContext.toDto()
          currentTraceContext = currentTraceContext.parentTraceContext
        }

        topicSender.sendNewProcessEvent(loggedProcess, traceHierarchy)

        // PY-89717: avoid awaitExit() on this app-scoped coroutine to stop
        // ThreadLeakTracker false-positives. See processAwaiter.kt for the full rationale.
        // (LoggingProcess.onExit() below already delegates to the JDK process reaper.)
        onExit().await()

        topicSender.sendProcessExitEvent(
          processId = loggedProcess.id,
          exitedAt = Clock.System.now(),
          exitValue = exitValue(),
        )
      }
    }
  }

  override fun getOutputStream(): OutputStream =
    cfg.backingProcess.outputStream

  override fun getInputStream(): InputStream =
    stdoutStream

  override fun getErrorStream(): InputStream =
    stderrStream

  override fun waitFor(): Int {
    return cfg.backingProcess.waitFor()
  }

  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    return cfg.backingProcess.waitFor(timeout, unit)
  }

  override fun exitValue(): Int =
    cfg.backingProcess.exitValue()

  override fun destroy(): Unit =
    cfg.backingProcess.destroy()

  override fun destroyForcibly(): Process? =
    cfg.backingProcess.destroyForcibly()

  override fun toHandle(): ProcessHandle? =
    cfg.backingProcess.toHandle()

  // PY-89717: delegate to backingProcess.onExit() (which on JDK ProcessImpl uses
  // ProcessHandleImpl.completion / the whitelisted "process reaper" daemon).
  // Default java.lang.Process.onExit() would do CompletableFuture.supplyAsync(this::waitForInternal),
  // spawning a ForkJoinPool worker stuck in ProcessImpl.waitFor.
  override fun onExit(): CompletableFuture<Process> =
    cfg.backingProcess.onExit().thenApply { this }

  override fun supportsNormalTermination(): Boolean =
    cfg.backingProcess.supportsNormalTermination()

  companion object {
    private val nextId: AtomicInteger = AtomicInteger(0)

    operator fun invoke(
      backingProcess: Process,
      weight: ConcurrentProcessWeight?,
      traceContext: TraceContext?,
      startedAt: Instant,
      cwd: String?,
      exe: Exe,
      args: List<String>,
      env: Map<String, String>,
      target: String,
      topicSender: ProcessOutputTopicSender,
      coroutineScope: CoroutineScope = ApplicationManager.getApplication().service<LoggingService>().scope,
    ): LoggingProcessImpl {
      val cfg = LoggingConfig(backingProcess, weight, traceContext, startedAt, cwd, exe, args, env, target, topicSender, coroutineScope)
      return when (backingProcess) {
        // The platform checks the process class for `SelfKiller` and `ProcessTreeKiller`.
        // A wrapper must implement the same interfaces as the process that it wraps.
        // If not, `KillableProcessHandler` uses OS tools to kill a remote (ijent-based) process.
        is ProcessTreeKiller -> {
          SelfTreeKillingLoggingProcess(backingProcess, cfg)
        }
        is SelfKiller -> {
          SelfKillingLoggingProcess(backingProcess, cfg)
        }
        else -> {
          NonSelfKillingLoggingProcess(cfg)
        }
      }
    }
  }
}

private class NonSelfKillingLoggingProcess(
  cfg: LoggingConfig,
) : LoggingProcessImpl(cfg)

private class SelfKillingLoggingProcess(
  private val selfKiller: SelfKiller,
  cfg: LoggingConfig,
) : LoggingProcessImpl(cfg), SelfKiller by selfKiller

private class SelfTreeKillingLoggingProcess(
  private val selfKiller: ProcessTreeKiller,
  cfg: LoggingConfig,
) : LoggingProcessImpl(cfg), ProcessTreeKiller by selfKiller, SelfKiller

internal class LoggingInputStream(
  private val processId: ProcessId,
  internal val backingInputStream: InputStream,
  private val kind: OutputKindDto,
  private val topicSender: ProcessOutputTopicSender,
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

    topicSender.sendNewOutputLineEvent(
      processId = processId,
      OutputLineDto(
        kind = kind,
        text = line,
      )
    )

    currentLineBytes = ByteStreams.newDataOutput()
  }

  private fun finalizeLastLine() {
    val bytes = currentLineBytes.toByteArray()

    if (bytes.isNotEmpty()) {
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
