// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public final class PerformanceTestSpan {
  public static final String SPAN_NAME = "performance_test";
  public static final IJTracer TRACER = TelemetryManager.getInstance().getTracer(
    new com.intellij.platform.diagnostic.telemetry.Scope("performance-plugin", null));
  public static final IJTracer WARMUP_TRACER = new WarmupIJTracer(TRACER);
  private static Span performanceTestSpan;
  private static Scope performanceScope;

  static void startSpan() {
    performanceTestSpan = TRACER.spanBuilder(SPAN_NAME).setNoParent().startSpan();
    performanceScope = performanceTestSpan.makeCurrent();
  }

  static void endSpan() {
    if (performanceScope != null) performanceScope.close();
    if (performanceTestSpan != null) performanceTestSpan.end();
  }

  @SuppressWarnings("resource")
  public static void makeTestSpanCurrent(){
    if(performanceTestSpan != null)  performanceTestSpan.makeCurrent();
  }

  public static Context getContext() {
    if (performanceTestSpan != null) return Context.current().with(performanceTestSpan);
    return Context.current();
  }

  public static IJTracer getTracer(boolean noopTracer) {
    return noopTracer ? WARMUP_TRACER : TRACER;
  }
}
