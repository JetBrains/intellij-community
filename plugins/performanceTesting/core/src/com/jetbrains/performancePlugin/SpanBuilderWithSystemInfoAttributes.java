// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.jetbrains.performancePlugin.utils.JvmRuntimeUtils;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class SpanBuilderWithSystemInfoAttributes implements SpanBuilder {
  private final SpanBuilder spanBuilder;

  public SpanBuilderWithSystemInfoAttributes(SpanBuilder spanBuilder) {
    this.spanBuilder = spanBuilder;
  }

  @Override
  public SpanBuilder setParent(@NotNull Context context) {
    return spanBuilder.setParent(context);
  }

  @Override
  public SpanBuilder setNoParent() {
    return spanBuilder.setNoParent();
  }

  @Override
  public SpanBuilder addLink(@NotNull SpanContext spanContext) {
    return spanBuilder.addLink(spanContext);
  }

  @Override
  public SpanBuilder addLink(@NotNull SpanContext spanContext, @NotNull Attributes attributes) {
    return spanBuilder.addLink(spanContext, attributes);
  }

  @Override
  public SpanBuilder setAttribute(@NotNull String key, @NotNull String value) {
    return spanBuilder.setAttribute(key, value);
  }

  @Override
  public SpanBuilder setAttribute(@NotNull String key, long value) {
    return spanBuilder.setAttribute(key, value);
  }

  @Override
  public SpanBuilder setAttribute(@NotNull String key, double value) {
    return spanBuilder.setAttribute(key, value);
  }

  @Override
  public SpanBuilder setAttribute(@NotNull String key, boolean value) {
    return spanBuilder.setAttribute(key, value);
  }

  @Override
  public <T> SpanBuilder setAttribute(@NotNull AttributeKey<T> key, @NotNull T value) {
    return spanBuilder.setAttribute(key, value);
  }

  @Override
  public SpanBuilder setSpanKind(@NotNull SpanKind spanKind) {
    return spanBuilder.setSpanKind(spanKind);
  }

  @Override
  public SpanBuilder setStartTimestamp(long startTimestamp, @NotNull TimeUnit unit) {
    return spanBuilder.setStartTimestamp(startTimestamp, unit);
  }

  @Override
  public Span startSpan() {
    long jitTime = JvmRuntimeUtils.INSTANCE.getJitTime();
    long gcTime = JvmRuntimeUtils.INSTANCE.getGCTime();
    return new SpanWithOnEndListener(spanBuilder.startSpan(), span -> {
      span.setAttribute("jitTime", JvmRuntimeUtils.INSTANCE.getJitTime() - jitTime);
      span.setAttribute("gcTime", JvmRuntimeUtils.INSTANCE.getGCTime() - gcTime);
    });
  }
}
