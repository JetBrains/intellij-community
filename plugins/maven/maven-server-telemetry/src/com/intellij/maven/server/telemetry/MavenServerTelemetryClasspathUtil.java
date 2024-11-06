// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.telemetry;

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Arrays;
import java.util.Collection;

public final class MavenServerTelemetryClasspathUtil {

  public static final Collection<Class<?>> TELEMETRY_CLASSES = Arrays.asList(
    SpanExporter.class,
    TextMapPropagator.class,
    OpenTelemetry.class,
    OpenTelemetrySdk.class,
    Clock.class,
    SdkMeterProvider.class,
    SdkLoggerProvider.class,
    ExtendedTracer.class,
    TelemetryContext.class
  );
}
