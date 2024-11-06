// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.telemetry;

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.ParallelRunnerForServer;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface MavenServerOpenTelemetry {
  <T> T callWithSpan(@NotNull String spanName, @NotNull Supplier<T> fn);

  <T, R> List<R> execute(boolean inParallel, @NotNull Collection<T> collection, @NotNull Function<T, R> method);

  <T, R> List<R> executeWithSpan(@NotNull String spanName,
                                 boolean inParallel,
                                 @NotNull Collection<T> collection,
                                 @NotNull Function<T, R> method);

  void shutdown();

  static @NotNull MavenServerOpenTelemetry from(@NotNull TelemetryContext context) {
    return new MavenServerOpenTelemetryImpl(context);
  }
}

final class MavenServerOpenTelemetryImpl implements MavenServerOpenTelemetry {

  private static final String INSTRUMENTATION_NAME = "MavenServer";

  private final @NotNull OpenTelemetry myOpenTelemetry = GlobalOpenTelemetry.get();
  private final @NotNull Tracer tracer;
  private final @NotNull Scope myScope;

  MavenServerOpenTelemetryImpl(@NotNull TelemetryContext remoteContext) {
    Context context = remoteContext.extract(myOpenTelemetry.getPropagators().getTextMapPropagator());
    myScope = context.makeCurrent();
    tracer = myOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
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

  @Override
  public void shutdown() {
    myScope.close();
  }
}

