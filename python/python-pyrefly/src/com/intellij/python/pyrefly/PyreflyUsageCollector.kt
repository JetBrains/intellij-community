package com.intellij.python.pyrefly

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getPythonInfo
import kotlin.time.Duration

object PyreflyUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.pyrefly.type.inference", 7)
  private val DURATION = EventFields.Int("duration_basket", "Duration of string type resolution, grouped into 100ms time buckets")
  private val SUCCESS = EventFields.Boolean("success")
  private val PYTHON_VERSION = EventFields.Version

  private val STRING_TYPE_RESOLUTION_TIME = GROUP.registerEvent("string.type.resolution.time", DURATION)
  private val PYREFLY_AUTO_INSTALLED = GROUP.registerEvent("pyrefly.auto.installed", SUCCESS)
  private val SERVER_STARTUP = GROUP.registerVarargEvent("server.startup", SUCCESS, PYTHON_VERSION)

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

  override fun getGroup(): EventLogGroup = GROUP
}
