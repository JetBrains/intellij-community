// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.idea.TestFor
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.python.pyrefly.PyreflyUsageCollector
import com.intellij.python.pyrefly.lsp.PyreflyServerHeartbeat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val GROUP_ID = "python.pyrefly.type.inference"
private const val EVENT_ID = "server.heartbeat"

/**
 * The heartbeat reports the resources of a running Pyrefly server process and stops with the process.
 * The process under test is a JVM that sleeps, because a JVM has a resident set size of many megabytes on every OS.
 */
@TestApplication
@TestFor(classes = [PyreflyServerHeartbeat::class, PyreflyUsageCollector::class], issues = ["PY-93038"])
@Subsystems.Typing
@Components.Pyrefly
@Layers.Functional
internal class PyreflyServerHeartbeatTest {
  private val projectFixture = projectFixture()
  private val sourcePath = tempPathFixture(prefix = "sleeper")

  @Test
  @RegistryKey("pyrefly.heartbeat.interval.ms", "1000")
  fun `reports the memory of a running process and stops when the process ends`(@TestDisposable disposable: Disposable) {
    val handler = OSProcessHandler(sleepingJvmCommandLine())
    handler.startNotify()
    val events = try {
      FUCollectorTestCase.collectLogEvents(disposable) {
        timeoutRunBlocking(60.seconds) {
          val job = PyreflyServerHeartbeat.getInstance(projectFixture.get()).track(handler)
          assertNotNull(job, "A local process is tracked")
          delay(3500.milliseconds)
          handler.destroyProcess()
          // The job ends with the process. A job that outlives the process leaks a coroutine per server restart.
          withTimeout(30.seconds) { job!!.join() }
        }
      }
    }
    finally {
      handler.destroyProcess()
      handler.waitFor(30_000)
    }

    val heartbeats = events.filter { it.isHeartbeat() }
    assertFalse(heartbeats.isEmpty(), "At least one heartbeat is reported during 3.5 seconds with a one second interval")
    for (heartbeat in heartbeats) {
      val data = heartbeat.event.data
      assertTrue(data.long("rss_mb") > 0, "A JVM holds more than one megabyte: $data")
      assertTrue(data.long("uptime_ms") >= 1000, "The uptime covers at least one interval: $data")
      assertEquals(1L, data.long("server_count"), "The only tracked process counts itself: $data")
      // The OS reports the CPU time of a process it has only just started late or never, so the CPU pair is optional.
      assertEquals(data.containsKey("cpu_time_ms"), data.containsKey("time_since_last_measurement_ms"), "The CPU pair travels together: $data")
      if (data.containsKey("cpu_time_ms")) {
        assertTrue(data.long("cpu_time_ms") >= 0, "The CPU time since the previous reading is not negative: $data")
        assertTrue(data.long("time_since_last_measurement_ms") >= 1000, "The CPU delta covers at least one interval: $data")
        assertTrue(data.long("uptime_ms") >= data.long("time_since_last_measurement_ms"), "The uptime covers the CPU interval: $data")
      }
    }
  }

  @Test
  fun `the heartbeat event reports megabytes and leaves out the CPU pair the OS did not report`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PyreflyUsageCollector.logServerHeartbeat(
        project = projectFixture.get(),
        residentSetSizeBytes = 300L * 1024 * 1024,
        cpuTime = null,
        uptimeMs = 120_000,
        serverCount = 3,
      )
    }
    val data = events.single { it.isHeartbeat() }.event.data
    assertEquals(300L, data.long("rss_mb"))
    assertFalse(data.containsKey("cpu_time_ms"), "A missing metric is left out instead of reported as zero: $data")
    assertFalse(data.containsKey("time_since_last_measurement_ms"), "The interval has no meaning without the CPU delta: $data")
    assertEquals(120_000L, data.long("uptime_ms"))
    assertEquals(3L, data.long("server_count"))
  }

  @Test
  fun `the heartbeat event reports the CPU delta with the interval it covers`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PyreflyUsageCollector.logServerHeartbeat(
        project = projectFixture.get(),
        residentSetSizeBytes = null,
        cpuTime = PyreflyUsageCollector.CpuTimeDelta(cpuTimeMs = 1_500, intervalMs = 120_000),
        uptimeMs = 180_000,
        serverCount = 1,
      )
    }
    val data = events.single { it.isHeartbeat() }.event.data
    assertFalse(data.containsKey("rss_mb"), "A missing metric is left out instead of reported as zero: $data")
    assertEquals(1_500L, data.long("cpu_time_ms"))
    assertEquals(120_000L, data.long("time_since_last_measurement_ms"))
    assertEquals(180_000L, data.long("uptime_ms"))
    assertEquals(1L, data.long("server_count"))
  }

  /**
   * A project can run more than one server, because a module whose environment pins another Pyrefly version
   * gets a server of its own. Each heartbeat carries the number of servers running in the project, so a
   * report can add up the memory of a project or follow one server.
   */
  @Test
  @RegistryKey("pyrefly.heartbeat.interval.ms", "1000")
  fun `the server count follows the tracked processes of the project`(@TestDisposable disposable: Disposable) {
    val first = OSProcessHandler(sleepingJvmCommandLine())
    val second = OSProcessHandler(sleepingJvmCommandLine())
    first.startNotify()
    second.startNotify()
    // The listener sees each event as it is logged, so the count of the phase can be attached to it.
    var firstIsRunning = true
    val serverCountsWithTwoProcesses = mutableListOf<Long>()
    val serverCountsWithOneProcess = mutableListOf<Long>()
    try {
      FUCollectorTestCase.listenForEvents("FUS", disposable, { event ->
        if (!event.isHeartbeat()) return@listenForEvents
        val serverCount = event.event.data.long("server_count")
        synchronized(serverCountsWithTwoProcesses) {
          if (firstIsRunning) serverCountsWithTwoProcesses.add(serverCount) else serverCountsWithOneProcess.add(serverCount)
        }
      }) {
        timeoutRunBlocking(60.seconds) {
          val heartbeat = PyreflyServerHeartbeat.getInstance(projectFixture.get())
          val firstJob = heartbeat.track(first)
          val secondJob = heartbeat.track(second)
          assertNotNull(firstJob, "A local process is tracked")
          assertNotNull(secondJob, "A local process is tracked")
          delay(2500.milliseconds)
          // The flag flips before the process ends. A heartbeat of the second process logged between the end
          // and the flip would otherwise land in the two-process bucket with a count of one.
          synchronized(serverCountsWithTwoProcesses) { firstIsRunning = false }
          first.destroyProcess()
          withTimeout(30.seconds) { firstJob!!.join() }
          delay(2500.milliseconds)
          second.destroyProcess()
          withTimeout(30.seconds) { secondJob!!.join() }
        }
      }
    }
    finally {
      first.destroyProcess()
      second.destroyProcess()
      first.waitFor(30_000)
      second.waitFor(30_000)
    }

    assertFalse(serverCountsWithTwoProcesses.isEmpty(), "At least one heartbeat is reported while two processes run")
    assertEquals(setOf(2L), serverCountsWithTwoProcesses.toSet(), "Each heartbeat counts both running servers")
    assertFalse(serverCountsWithOneProcess.isEmpty(), "At least one heartbeat is reported after the first process ended")
    // A heartbeat that read the count just before the first process ended can still be logged after it. The last one is past that.
    assertEquals(1L, serverCountsWithOneProcess.last(), "The count drops when a server ends: $serverCountsWithOneProcess")
  }

  private fun LogEvent.isHeartbeat(): Boolean = group.id == GROUP_ID && event.id == EVENT_ID

  private fun Map<String, Any>.long(key: String): Long = (this[key] as Number).toLong()

  /** Runs a single-file Java program with the JDK of the current process. The program sleeps for a minute. */
  private fun sleepingJvmCommandLine(): GeneralCommandLine {
    val source = sourcePath.get().resolve("Sleeper.java")
    source.writeText("""
      public class Sleeper {
        public static void main(String[] args) throws InterruptedException {
          Thread.sleep(60_000);
        }
      }
    """.trimIndent())
    val java = ProcessHandle.current().info().command().orElseThrow()
    return GeneralCommandLine(java, "-Xmx64m", source.toString())
  }
}
