package com.intellij.ide.starter.telemetry

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider

class NoopTestTelemetryService : TestTelemetryService {
  override fun getTracer(): Tracer {
    return TracerProvider.noop().get("")
  }

  override fun shutdown() {

  }
}