// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:org.jetbrains.annotations.ApiStatus.Internal

package com.intellij.ui.webview.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.measureTimedValue

fun Logger.webViewLifecycle(event: String, details: String = "") {
  trace {
    if (details.isNotEmpty()) {
      "lifecycle: $event - $details"
    }
    else {
      "lifecycle: $event"
    }
  }
}

inline fun <T> Logger.traceWebViewPerf(metric: String, details: String = "", action: () -> T): T {
  val timedResult = measureTimedValue { runCatching(action) }
  traceWebViewPerf(metric, timedResult.duration, details)
  return timedResult.value.getOrThrow()
}

fun Logger.traceWebViewPerfSince(metric: String, startedAt: TimeMark, details: String = "") {
  traceWebViewPerf(metric, startedAt.elapsedNow(), details)
}

fun Logger.traceWebViewPerf(metric: String, duration: Duration, details: String = "") {
  trace { webViewPerfMessage(metric, duration, details) }
}

private fun webViewPerfMessage(metric: String, duration: Duration, details: String): String {
  val message = "perf: $metric = ${duration.inWholeMilliseconds}ms"
  return if (details.isEmpty()) message else "$message - $details"
}
