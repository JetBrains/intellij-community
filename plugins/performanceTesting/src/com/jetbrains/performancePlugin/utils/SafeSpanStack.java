// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.utils;

import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public class SafeSpanStack implements Disposable {
  private static final Logger LOG = Logger.getInstance(SafeSpanStack.class);

  private final Deque<Span> myStack = new ArrayDeque<>();
  private final IJTracer myTracer;
  private final Context myUpperContext;

  public SafeSpanStack(@NotNull IJTracer tracer, @Nullable Context upperContext) {
    myTracer = tracer;
    myUpperContext = upperContext == null
                     ? Context.current()
                     : upperContext;
  }

  @Nullable
  synchronized public Span getCurrentSpan() {
    return myStack.peek();
  }

  synchronized public void startSpan(@NotNull String activityName) {
    //noinspection DataFlowIssue
    myStack.push(myTracer
                   .spanBuilder(activityName)
                   .setParent(myStack.isEmpty() ? myUpperContext
                                                : Context.current().with(myStack.peek()))
                   .startSpan());
  }

  synchronized public void endSpan(@NonNls @Nullable String errDescriptionOrNull) {
    if (myStack.isEmpty()) {
      LOG.warn("Lost span start!");
    }
    else {
      final Span span = myStack.pop();
      if (errDescriptionOrNull != null) {
        span.setStatus(StatusCode.ERROR, errDescriptionOrNull);
      }
      span.end();
    }
  }

  @Override
  synchronized public void dispose() {
    while (!myStack.isEmpty()) {
      endSpan("External span termination!");
    }
  }
}
