package com.jetbrains.performancePlugin.utils

import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import java.util.concurrent.TimeUnit
import com.intellij.openapi.util.Pair

class HighlightingTestUtil {

  companion object {

    @JvmStatic
    fun storeProcessFinishedTime(scopeName: String, spanName: String, vararg additionalAttributes: Pair<String, String>) {
      val span = TelemetryManager.getTracer(Scope(scopeName))
        .spanBuilder(spanName)
        .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .startSpan()
        .setAttribute("finish", System.currentTimeMillis())
      additionalAttributes.forEach { attributesPair -> span.setAttribute(attributesPair.first, attributesPair.second) }
      span.end(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }
  }

}