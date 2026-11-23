// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val serviceTimeout = 24.hours
private val readinessPollInterval = 200.milliseconds
private val healthCheckInterval = 1.seconds

/** Owns the service processes for one Wayland session. */
internal class WaylandSessionProcesses(parentScope: CoroutineScope, private val environment: Map<String, String>) {
  private val sessionJob = SupervisorJob(requireNotNull(parentScope.coroutineContext[Job]))
  @Suppress("RAW_SCOPE_CREATION") // The session job belongs to the supplied process scope.
  private val scope = CoroutineScope(parentScope.coroutineContext + sessionJob)
  private val services = mutableListOf<Service>()

  private data class Service(val name: String, val process: Process, val completion: Deferred<Int>, val logFile: Path)

  val isAlive: Boolean
    get() = scope.isActive && services.isNotEmpty() && services.all { it.process.isAlive && it.completion.isActive }

  /** Stops all services before the action returns or throws. */
  suspend fun <T> use(action: suspend () -> T): T {
    try {
      return action()
    }
    finally {
      stop()
    }
  }

  suspend fun start(name: String, args: List<String>, logFile: Path, input: ByteArray = byteArrayOf()) {
    val started = CompletableDeferred<Process>()
    val completion = scope.async(Dispatchers.IO) {
      ProcessExecutor(
        presentableName = "Start $name",
        args = args,
        workDir = null,
        timeout = serviceTimeout,
        environmentVariables = environment,
        stdoutRedirect = ExecOutputRedirect.ToFile(logFile),
        stderrRedirect = ExecOutputRedirect.ToFile(logFile),
        onProcessCreated = { process, _ -> started.complete(process) },
        stdInBytes = input,
        analyzeProcessExit = false,
      ).startCancellable(printEnvVariables = false)
    }
    val process = select {
      started.onAwait { it }
      completion.onAwait { code -> error("$name exited with code $code before startup; log: $logFile") }
    }
    services.add(Service(name, process, completion, logFile))
    checkAlive()
  }

  fun checkAlive() {
    check(scope.isActive) { "The Wayland session scope is no longer active" }
    services.firstOrNull { !it.process.isAlive || it.completion.isCompleted }?.let { service ->
      val output = if (service.logFile.exists()) {
        service.logFile.bufferedReader().useLines { it.take(100).joinToString("\n") }
      }
      else ""
      error("${service.name} stopped; log: ${service.logFile}\n$output")
    }
  }

  suspend fun awaitReady(description: String, timeout: Duration, isReady: suspend () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
      checkAlive()
      if (isReady()) {
        checkAlive()
        return
      }
      check(start.elapsedNow() < timeout) {
        "Timed out waiting for $description after $timeout; logs: ${services.joinToString { it.logFile.toString() }}"
      }
      delay(readinessPollInterval)
    }
  }

  suspend fun runCommand(args: List<String>, timeout: Duration, silent: Boolean = false): String = withContext(Dispatchers.IO) {
    checkAlive()
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = if (silent) "Configure GNOME Remote Desktop" else args.joinToString(" "),
      args = args,
      workDir = null,
      timeout = timeout,
      environmentVariables = environment,
      stdoutRedirect = stdout,
      stderrRedirect = ExecOutputRedirect.ToString(),
      silent = silent,
    ).startCancellable(printEnvVariables = false)
    checkAlive()
    stdout.read()
  }

  /** Cancels the IDE run if a service stops while the IDE runs. */
  suspend fun <T> runWithHealthCheck(action: suspend () -> T): T = coroutineScope {
    checkAlive()
    val monitor = launch(Dispatchers.IO) {
      while (isActive) {
        checkAlive()
        delay(healthCheckInterval)
      }
    }
    try {
      action().also { checkAlive() }
    }
    finally {
      withContext(NonCancellable) { monitor.cancelAndJoin() }
    }
  }

  suspend fun stop() {
    withContext(NonCancellable + Dispatchers.IO) {
      services.asReversed().forEach { it.completion.cancelAndJoin() }
      sessionJob.cancelAndJoin()
    }
  }
}