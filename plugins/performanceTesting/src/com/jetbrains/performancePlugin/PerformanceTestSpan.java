package com.jetbrains.performancePlugin;

import com.intellij.diagnostic.telemetry.IJTracer;
import com.intellij.diagnostic.telemetry.TraceManager;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class PerformanceTestSpan {
  public static final String SPAN_NAME = "performance_test";
  public final static IJTracer TRACER = TraceManager.INSTANCE.getTracer("performance-plugin");
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

  public static Context getContext(){
    if (performanceTestSpan != null) return Context.current().with(performanceTestSpan);
    return null;
  }
}
