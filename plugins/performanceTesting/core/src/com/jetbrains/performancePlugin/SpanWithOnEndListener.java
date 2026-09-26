// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class SpanWithOnEndListener implements Span {
  private final Span span;
  private final OnEndListener<? super Span> listener;

  public SpanWithOnEndListener(Span span, OnEndListener<? super Span> listener) {
    this.span = span;
    this.listener = listener;
  }

  @Override
  public <T> Span setAttribute(@NotNull AttributeKey<T> key, @NotNull T value) {
    return span.setAttribute(key, value);
  }

  @Override
  public Span addEvent(@NotNull String name, @NotNull Attributes attributes) {
    return span.addEvent(name, attributes);
  }

  @Override
  public Span addEvent(@NotNull String name, @NotNull Attributes attributes, long timestamp, @NotNull TimeUnit unit) {
    return span.addEvent(name, attributes, timestamp, unit);
  }

  @Override
  public Span setStatus(@NotNull StatusCode statusCode, @NotNull String description) {
    return span.setStatus(statusCode, description);
  }

  @Override
  public Span recordException(@NotNull Throwable exception, @NotNull Attributes additionalAttributes) {
    return span.recordException(exception, additionalAttributes);
  }

  @Override
  public Span updateName(@NotNull String name) {
    return span.updateName(name);
  }

  @Override
  public void end() {
    listener.onEnd(this);
    span.end();
  }

  @Override
  public void end(long timestamp, @NotNull TimeUnit unit) {
    listener.onEnd(this);
    span.end(timestamp, unit);
  }

  @Override
  public SpanContext getSpanContext() {
    return span.getSpanContext();
  }

  @Override
  public boolean isRecording() {
    return span.isRecording();
  }
}
