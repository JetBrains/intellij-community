// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import io.opentelemetry.api.trace.SpanBuilder

/**
 * Create spans with the attribute 'warmup` warmup span doesn't use in metrics processing
 * and mean that this action was produced for IDE warmup
 */
class WarmupIJTracer(private val tracer: IJTracer) : IJTracer {
  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder {
    return tracer.spanBuilder(spanName, level).also {
      setWarmup(it)
    }
  }

  override fun spanBuilder(spanName: String): SpanBuilder {
    return tracer.spanBuilder(spanName).also {
      setWarmup(it)
    }
  }

  private fun setWarmup(span: SpanBuilder) {
    span.setAttribute("warmup", true)
  }
}