// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackCommandReporter;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SystemProperties;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * OpenTelemetry reporter for the {@code ScriptRunner}.
 * This reporter wraps the script runner and each executed script command in open telemetry spans.
 * Can be chained with {@code ReporterWithTimer}
 * <br>
 * Activated by the system property {@code "performance.execute.script.reportEachCommandAsTelemetrySpan"}.
 * <br>
 * If the system property {@code "performance.execute.script.reportScriptRunnerAsRootSpan"} is {@code true},
 * the "Script Runner" span is a root.
 */

public class ReporterCommandAsTelemetrySpan implements PlaybackCommandReporter {
  private SafeSpanStack myOTSpanLogger;
  private Span mySpan;
  private final PlaybackCommandReporter myChainedReporter;

  public static final String USE_SPAN_WRAPPER_FOR_COMMAND = "performance.execute.script.reportEachCommandAsTelemetrySpan";
  public static final String REPORT_RUNNER_SPAN_AS_ROOT = "performance.execute.script.reportScriptRunnerAsRootSpan";

  public ReporterCommandAsTelemetrySpan() {
    this(EMPTY_PLAYBACK_COMMAND_REPORTER);
  }

  public ReporterCommandAsTelemetrySpan(@NotNull PlaybackCommandReporter chainedReporter) {
    myChainedReporter = chainedReporter;
  }

  @Override
  public void startOfCommand(@NotNull String fullCommandLine) {
    myChainedReporter.startOfCommand(fullCommandLine);
    myOTSpanLogger.startSpan(fullCommandLine);
  }

  @Override
  public void endOfCommand(@Nullable String errDescriptionOrNull) {
    myOTSpanLogger.endSpan(errDescriptionOrNull);
    myChainedReporter.endOfCommand(errDescriptionOrNull);
  }

  @Override
  public void startOfScript(@Nullable Project project) {
    myChainedReporter.startOfScript(project);

    SpanBuilder spanBuilder = PerformanceTestSpan.TRACER.spanBuilder("Script Runner");
    if (SystemProperties.getBooleanProperty(REPORT_RUNNER_SPAN_AS_ROOT, false)) {
      spanBuilder = spanBuilder.setNoParent();
    }
    mySpan = spanBuilder.startSpan();
    myOTSpanLogger = new SafeSpanStack(PerformanceTestSpan.TRACER, Context.current().with(mySpan));
  }

  @Override
  public void scriptCanceled() {
    myChainedReporter.scriptCanceled();
  }

  @Override
  public void endOfScript(@Nullable Project project) {
    Disposer.dispose(myOTSpanLogger);
    mySpan.end();
    myChainedReporter.endOfScript(project);
  }
}
