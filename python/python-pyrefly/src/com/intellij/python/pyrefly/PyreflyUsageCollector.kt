package com.intellij.python.pyrefly

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
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

  fun logServerStartup(success: Boolean, sdk: Sdk?) {
    val fields = buildList {
      add(SUCCESS with success)
      sdk.pythonVersion()?.let {
        add(PYTHON_VERSION with it.toPythonVersion())
      }
    }
    SERVER_STARTUP.log(*fields.toTypedArray())
  }

  private fun Sdk?.pythonVersion(): LanguageLevel? {
    if (this == null || !PythonSdkUtil.isPythonSdk(this)) return null

    return PythonSdkFlavor.getFlavor(this)
             ?.getLanguageLevel(this)
           ?: versionString?.let(LanguageLevel::fromPythonVersion)
  }

  override fun getGroup(): EventLogGroup = GROUP
}
