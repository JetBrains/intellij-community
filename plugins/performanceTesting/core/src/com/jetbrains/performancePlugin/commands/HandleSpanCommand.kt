// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.trace.Span

/**
 *  The first call will create and start span.
 *  The second call with the same spanName will stop span.
 *  !!!The hierarchy of spans can't be created using it since there is no propagation of the scope.
 * */
class HandleSpanCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "handleSpan"
    const val PREFIX = "$CMD_PREFIX$NAME"

    private val createdSpans: MutableMap<String, Span> =  mutableMapOf()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val spanName = extractCommandArgument(PREFIX)
    val span = createdSpans[spanName]
    if (span != null) {
      span.end()
      createdSpans.remove(spanName)
    } else {
      val newSpan = TelemetryManager.getTracer(Scope("HandleSpanCommand"))
        .spanBuilder(spanName)
        .startSpan()
      createdSpans[spanName] = newSpan
    }
  }

  override fun getName(): String {
    return NAME
  }
}