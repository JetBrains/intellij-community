// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.telemetry;

import com.intellij.util.ArrayUtilRt;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.LongRunningTaskInput;
import org.jetbrains.idea.maven.server.ParallelRunnerForServer;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface MavenServerOpenTelemetry {
  MavenServerOpenTelemetry NOOP = new MavenServerOpenTelemetryNoop();

  <T> T callWithSpan(@NotNull String spanName, @NotNull Supplier<T> fn);

  <T, R> List<R> execute(boolean inParallel, @NotNull Collection<T> collection, @NotNull Function<T, R> method);

  <T, R> List<R> executeWithSpan(@NotNull String spanName,
                                 boolean inParallel,
                                 @NotNull Collection<T> collection,
                                 @NotNull Function<T, R> method);

  byte[] shutdown();

  static MavenServerOpenTelemetry of(@NotNull LongRunningTaskInput input) {
    String traceId = input.getTelemetryTraceId();
    String parentSpanId = input.getTelemetryParentSpanId();
    if (null == traceId || null == parentSpanId) {
      return NOOP;
    }
    return new MavenServerOpenTelemetryImpl(traceId, parentSpanId);
  }
}

final class MavenServerOpenTelemetryNoop implements MavenServerOpenTelemetry {
  @Override
  public <T> T callWithSpan(@NotNull String spanName, @NotNull Supplier<T> fn) {
    return fn.get();
  }

  @Override
  public <T, R> List<R> execute(boolean inParallel, @NotNull Collection<T> collection, @NotNull Function<T, R> method) {
    return ParallelRunnerForServer.execute(inParallel, collection, method);
  }

  @Override
  public <T, R> List<R> executeWithSpan(@NotNull String spanName,
                                        boolean inParallel,
                                        @NotNull Collection<T> collection,
                                        @NotNull Function<T, R> method) {
    return execute(inParallel, collection, method);
  }

  @Override
  public byte[] shutdown() {
    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }
}

final class MavenServerOpenTelemetryImpl implements MavenServerOpenTelemetry {

  private static final String INSTRUMENTATION_NAME = "MavenServer";

  private final @NotNull OpenTelemetry myOpenTelemetry;
  private final @NotNull MavenFilteringSpanDataCollector mySpanDataCollector = new MavenFilteringSpanDataCollector();
  private final @NotNull Tracer tracer;
  private final @NotNull Span rootSpan;
  private final @NotNull Scope myScope;

  MavenServerOpenTelemetryImpl(@NotNull String traceId, @NotNull String parentSpanId) {
    myOpenTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(SdkTracerProvider.builder()
                           .addSpanProcessor(BatchSpanProcessor.builder(mySpanDataCollector)
                                               .setMaxExportBatchSize(128)
                                               .build())
                           .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), INSTRUMENTATION_NAME)))
                           .build())
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .build();

    SpanContext parentSpanContext = SpanContext.createFromRemoteParent(
      traceId,
      parentSpanId,
      TraceFlags.getSampled(),
      TraceState.getDefault());

    Context context = Context.root().with(Span.wrap(parentSpanContext));

    tracer = myOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
    rootSpan = tracer.spanBuilder("MavenServerRootSpan")
      .setParent(context)
      .startSpan();

    myScope = rootSpan.makeCurrent();
  }

  public @NotNull OpenTelemetry getTelemetry() {
    return myOpenTelemetry;
  }

  @Override
  public <T> T callWithSpan(@NotNull String spanName, @NotNull Supplier<T> fn) {
    SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
    Span span = spanBuilder.startSpan();
    try (Scope ignore = span.makeCurrent()) {
      return fn.get();
    }
    catch (Exception e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
    }
    finally {
      span.end();
    }
  }

  @Override
  public <T, R> List<R> execute(boolean inParallel, @NotNull Collection<T> collection, @NotNull Function<T, R> method) {
    Context context = Context.current();

    // there are issues with passing context to common pool (used by parallelStream and ParallelRunnerForServer)
    // all spans in pool common threads do not get attached to parent span and get lost
    // so: either wrap the function or rewrite ParallelRunnerForServer to not use common pool
    return ParallelRunnerForServer.execute(inParallel, collection, context.wrapFunction(method));
  }

  @Override
  public <T, R> List<R> executeWithSpan(@NotNull String spanName,
                                        boolean inParallel,
                                        @NotNull Collection<T> collection,
                                        @NotNull Function<T, R> method) {
    return callWithSpan(spanName, () ->
      execute(inParallel, collection, method)
    );
  }

  public <T> T callWithSpan(@NotNull String spanName, @NotNull Function<Span, T> fn) {
    return callWithSpan(spanName, (ignore) -> {
    }, fn);
  }

  public <T> T callWithSpan(@NotNull String spanName,
                            @NotNull Consumer<SpanBuilder> configurator,
                            @NotNull Function<Span, T> fn) {
    SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
    configurator.accept(spanBuilder);
    Span span = spanBuilder.startSpan();
    try (Scope ignore = span.makeCurrent()) {
      return fn.apply(span);
    }
    catch (Exception e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
    }
    finally {
      span.end();
    }
  }

  public void runWithSpan(@NotNull String spanName, @NotNull Consumer<Span> consumer) {
    callWithSpan(spanName, span -> {
      consumer.accept(span);
      return null;
    });
  }

  @Override
  public byte[] shutdown() {
    try {
      rootSpan.end();
      myScope.close();
      if (myOpenTelemetry instanceof Closeable) {
        ((Closeable)myOpenTelemetry).close();
      }
      // the data should be exported only after OpenTelemetry was closed to prevent data loss
      Collection<SpanData> collectedSpans = mySpanDataCollector.getCollectedSpans();
      return MavenSpanDataSerializer.serialize(collectedSpans);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

