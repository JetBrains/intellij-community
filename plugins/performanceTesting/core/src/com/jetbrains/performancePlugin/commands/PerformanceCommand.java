// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.SpanBuilderWithSystemInfoAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;

import static com.fasterxml.jackson.module.kotlin.ExtensionsKt.jacksonObjectMapper;

public abstract class PerformanceCommand extends AbstractCommand {

  private static final String PREFIX = "%";
  private final ObjectMapper myObjectMapper = jacksonObjectMapper();
  private final Tracer tracer = isWarmupMode() ? PerformanceTestSpan.WARMUP_TRACER : PerformanceTestSpan.TRACER;

  public PerformanceCommand(@NotNull String text, int line) {
    super(text, line);
  }

  public PerformanceCommand(@NotNull String text, int line, boolean executeInAwt) {
    super(text, line, executeInAwt);
  }

  protected abstract String getName();

  protected String getPrefix() {
    return PREFIX + getName();
  }

  protected Boolean isWarmupMode() {
    return extractCommandArgument(getPrefix()).contains("WARMUP");
  }

  protected Boolean systemMetricsEnabled() {
    return extractCommandArgument(getPrefix()).contains("ENABLE_SYSTEM_METRICS");
  }

  private SpanBuilder wrapIfNeed(SpanBuilder spanBuilder) {
    if (systemMetricsEnabled()) {
      return new SpanBuilderWithSystemInfoAttributes(spanBuilder);
    }
    return spanBuilder;
  }

  protected Span startSpan(String name) {
    SpanBuilder spanBuilder = wrapIfNeed(tracer.spanBuilder(name));
    return spanBuilder.startSpan();
  }

  protected <T> T deserializeOptionsFromJson(String json, Class<T> clazz) {
    try {
      return myObjectMapper.readValue(json, clazz);
    }
    catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
