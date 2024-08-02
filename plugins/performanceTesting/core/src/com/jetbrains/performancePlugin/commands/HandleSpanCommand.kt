package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.trace.Span

/**
 *  The first call will create and start span.
 *  The second call with the same spanName will stop span.
 * */
class HandleSpanCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "handleSpan"
    const val PREFIX = "$CMD_PREFIX$NAME"

    private val CREATED_SPANS: MutableMap<String, Span> =  mutableMapOf()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val spanName = extractCommandArgument(PREFIX)
    val span = CREATED_SPANS[spanName]
    if (span != null) {
      span.end()
      CREATED_SPANS.remove(spanName)
    } else {
      val newSpan = TelemetryManager.getTracer(Scope("HandleSpanCommand"))
        .spanBuilder(spanName)
        .startSpan()
      CREATED_SPANS[spanName] = newSpan
    }
  }

  override fun getName(): String {
    return NAME
  }
}