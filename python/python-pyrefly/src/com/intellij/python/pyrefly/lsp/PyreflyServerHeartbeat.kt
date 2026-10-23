// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly.lsp

import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.util.system.ProcessMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private val LOG = logger<PyreflyServerHeartbeat>()

private const val INTERVAL_REGISTRY_KEY = "pyrefly.heartbeat.interval.ms"
private const val DEFAULT_INTERVAL_MS = 60_000
/** A shorter interval floods FUS with events. The registry value is clamped to this range. */
private const val MIN_INTERVAL_MS = 1_000
private const val MAX_INTERVAL_MS = 3_600_000

/**
 * Reports the memory and the CPU time of a running Pyrefly server process to FUS once per interval.
 * The interval is the registry value `pyrefly.heartbeat.interval.ms`, clamped to one second and one hour.
 * The report stops when the process ends or the project closes.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyreflyServerHeartbeat(private val project: Project, private val coroutineScope: CoroutineScope) {
  /**
   * The number of tracked server processes. One server serves every module of the project, and a module
   * whose environment pins another Pyrefly version gets a server of its own.
   */
  private val trackedServers = AtomicInteger()

  /**
   * Starts the heartbeat for the process of [handler].
   * The metrics come from the local OS, so a process in a remote environment is not tracked.
   *
   * @return the heartbeat job, or `null` when the process is not tracked
   */
  fun track(handler: BaseProcessHandler<*>): Job? {
    val process = localProcessHandle(handler.process) ?: return null
    trackedServers.incrementAndGet()
    val job = coroutineScope.launch(Dispatchers.IO) {
      reportUntilTerminated(process, handler)
    }
    // The job ends with the process or the project, so this handler leaves the count right in both cases.
    job.invokeOnCompletion { trackedServers.decrementAndGet() }
    handler.addProcessListener(object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        job.cancel()
      }
    })
    return job
  }

  /**
   * The handle of [process] when the process runs on this machine, else `null`.
   *
   * The readers use the local OS, so the check is on the process itself, not on the project or the SDK.
   * A process of another environment, for example WSL or Docker, reaches the IDE as an adapter. The adapter
   * refuses `pid()` by contract, because that PID belongs to the other machine, while its handle exposes
   * that PID. A local OS reading by that PID would describe an unrelated local process.
   *
   * The CPU reading goes through the handle, because it stays bound to one process. The resident set size
   * reader takes a PID, and the OS can give the PID of a process that ended to a new process. So a heartbeat
   * checks the handle again after the readings, and drops them when the process is gone.
   */
  private fun localProcessHandle(process: Process): ProcessHandle? {
    try {
      process.pid()
      return process.toHandle()
    }
    catch (e: UnsupportedOperationException) {
      LOG.debug("The Pyrefly server process runs in another environment, so its resources are not tracked", e)
      return null
    }
  }

  private suspend fun reportUntilTerminated(process: ProcessHandle, handler: BaseProcessHandler<*>) {
    val startedAt = System.nanoTime()
    // The OS often reports no CPU time for a process that has only just started. The process has used
    // none yet, so a zero baseline keeps the CPU of the first interval, where the startup indexing runs.
    var lastCpuSample = cpuSample(process, startedAt) ?: CpuSample(cpuTimeMs = 0, measuredAt = startedAt)
    while (isRunning(process, handler)) {
      delay(Registry.intValue(INTERVAL_REGISTRY_KEY, DEFAULT_INTERVAL_MS, MIN_INTERVAL_MS, MAX_INTERVAL_MS).milliseconds)
      // The handler sets its flag from its reader thread, so the handle is the check that is current.
      if (!isRunning(process, handler)) {
        return
      }
      val now = System.nanoTime()
      val residentSetSizeBytes = ProcessMetrics.residentSetSize(process.pid())
      val cpuSample = cpuSample(process, now)
      if (!process.isAlive) {
        // The process ended during the readings, so the PID can already name another process.
        return
      }
      if (residentSetSizeBytes == null && cpuSample == null) {
        // The OS reported nothing this time, so this heartbeat carries no data. The RSS reader can be switched
        // off for the session, and the CPU reading can be empty once, so the next tick can still report.
        // A process that ended stops the loop through the check above.
        continue
      }
      // The OS can skip a CPU reading. Then the next delta spans two intervals, and so does the time it is paired with.
      val cpuTime = if (cpuSample != null) {
        PyreflyUsageCollector.CpuTimeDelta(
          cpuTimeMs = (cpuSample.cpuTimeMs - lastCpuSample.cpuTimeMs).coerceAtLeast(0),
          intervalMs = (cpuSample.measuredAt - lastCpuSample.measuredAt).nanoseconds.inWholeMilliseconds,
        )
      }
      else {
        null
      }
      PyreflyUsageCollector.logServerHeartbeat(
        project = project,
        residentSetSizeBytes = residentSetSizeBytes,
        cpuTime = cpuTime,
        uptimeMs = (now - startedAt).nanoseconds.inWholeMilliseconds,
        serverCount = trackedServers.get(),
      )
      lastCpuSample = cpuSample ?: lastCpuSample
    }
  }

  /** The total CPU time of the process, with the `System.nanoTime()` of the reading. */
  private class CpuSample(val cpuTimeMs: Long, val measuredAt: Long)

  private fun cpuSample(process: ProcessHandle, measuredAt: Long): CpuSample? =
    process.info().totalCpuDuration().getOrNull()?.let { CpuSample(it.toMillis(), measuredAt) }

  private fun isRunning(process: ProcessHandle, handler: BaseProcessHandler<*>): Boolean =
    process.isAlive && !handler.isProcessTerminated

  companion object {
    fun getInstance(project: Project): PyreflyServerHeartbeat = project.service()
  }
}
