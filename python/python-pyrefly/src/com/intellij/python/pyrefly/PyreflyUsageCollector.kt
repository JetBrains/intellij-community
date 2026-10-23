package com.intellij.python.pyrefly

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getPythonInfo
import kotlin.time.Duration

object PyreflyUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.pyrefly.type.inference", 8)
  private val DURATION = EventFields.Int("duration_basket", "Duration of string type resolution, grouped into 100ms time buckets")
  private val SUCCESS = EventFields.Boolean("success")
  private val PYTHON_VERSION = EventFields.Version
  private val MEMORY_RSS_MB = EventFields.Long("rss_mb", "Resident set size of the Pyrefly server process in megabytes")
  private val CPU_TIME_MS = EventFields.Long("cpu_time_ms", "CPU time in milliseconds the Pyrefly server process used since the previous heartbeat")
  private val TIME_SINCE_LAST_MEASUREMENT_MS = EventFields.Long("time_since_last_measurement_ms", "Time in milliseconds the CPU time delta covers. Reported together with cpu_time_ms")
  private val UPTIME_MS = EventFields.Long("uptime_ms", "Time in milliseconds since the Pyrefly server process started")
  private val SERVER_COUNT = EventFields.Int("server_count", "Number of Pyrefly server processes running in the project, this one included. One server serves every module, and a module whose environment pins another Pyrefly version gets a server of its own")

  private val STRING_TYPE_RESOLUTION_TIME = GROUP.registerEvent("string.type.resolution.time", DURATION)
  private val PYREFLY_AUTO_INSTALLED = GROUP.registerEvent("pyrefly.auto.installed", SUCCESS)
  private val SERVER_STARTUP = GROUP.registerVarargEvent("server.startup", SUCCESS, PYTHON_VERSION)
  private val SERVER_HEARTBEAT = GROUP.registerVarargEvent("server.heartbeat", MEMORY_RSS_MB, CPU_TIME_MS, TIME_SINCE_LAST_MEASUREMENT_MS, UPTIME_MS, SERVER_COUNT)

  fun logStringTypeResolutionTime(duration: Duration) {
    val basket = duration.inWholeMilliseconds.toInt() / 100
    STRING_TYPE_RESOLUTION_TIME.log(basket)
  }

  fun logPyreflyAutoInstalled(success: Boolean) {
    PYREFLY_AUTO_INSTALLED.log(success)
  }

  /** Logs a start of the server for [interpreter]. The version comes from what the interpreter records, so nothing runs. */
  fun logServerStartup(success: Boolean, interpreter: PythonInterpreter?) {
    val fields = buildList {
      add(SUCCESS with success)
      interpreter?.getPythonInfo()?.successOrNull?.languageLevel?.let {
        add(PYTHON_VERSION with it.toPythonVersion())
      }
    }
    SERVER_STARTUP.log(*fields.toTypedArray())
  }

  /** The CPU time the server process used over [intervalMs]. The two values always travel together. */
  class CpuTimeDelta(val cpuTimeMs: Long, val intervalMs: Long)

  /**
   * Reports the resources of one running Pyrefly server process.
   * A `null` metric is left out, because the OS did not report it.
   * [serverCount] is the number of Pyrefly server processes running in the project, this one included.
   */
  fun logServerHeartbeat(project: Project, residentSetSizeBytes: Long?, cpuTime: CpuTimeDelta?, uptimeMs: Long, serverCount: Int) {
    val fields = buildList {
      residentSetSizeBytes?.let { add(MEMORY_RSS_MB with it / 1024 / 1024) }
      cpuTime?.let {
        add(CPU_TIME_MS with it.cpuTimeMs)
        add(TIME_SINCE_LAST_MEASUREMENT_MS with it.intervalMs)
      }
      add(UPTIME_MS with uptimeMs)
      add(SERVER_COUNT with serverCount)
    }
    SERVER_HEARTBEAT.log(project, *fields.toTypedArray())
  }

  override fun getGroup(): EventLogGroup = GROUP
}
