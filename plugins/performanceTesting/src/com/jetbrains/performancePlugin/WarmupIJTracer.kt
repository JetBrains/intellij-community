package com.jetbrains.performancePlugin

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.jetbrains.performancePlugin.utils.SpanAttributes
import io.opentelemetry.api.trace.SpanBuilder

class WarmupIJTracer(private val tracer: IJTracer): IJTracer {
  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder {
    return tracer.spanBuilder(spanName, level).also {
      SpanAttributes.setWarmup(it)
    }
  }

  override fun spanBuilder(spanName: String): SpanBuilder {
    return tracer.spanBuilder(spanName).also {
      SpanAttributes.setWarmup(it)
    }
  }
}