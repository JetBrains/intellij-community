package com.intellij.ide.starter.telemetry

import com.intellij.ide.starter.di.di
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.kodein.di.direct
import org.kodein.di.instance

interface TestTelemetryService {
  companion object {
    val instance: TestTelemetryService by lazy { di.direct.instance() }

    fun spanBuilder(spanName: String): SpanBuilder = instance.getTracer().spanBuilder(spanName)
  }

  fun getTracer(): Tracer

  fun shutdown()
}

inline fun <T> computeWithSpan(spanName: String, operation: (Span) -> T): T {
  val span = TestTelemetryService.spanBuilder(spanName).startSpan()
  try {
    return span.makeCurrent().use {
      operation(span)
    }
  }
  catch (e: Throwable) {
    span.recordException(e)
    span.setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    span.end()
  }
}