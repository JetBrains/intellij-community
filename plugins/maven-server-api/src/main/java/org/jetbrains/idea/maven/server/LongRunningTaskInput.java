// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class LongRunningTaskInput implements Serializable {
  @NotNull
  private final String longRunningTaskId;

  @Nullable
  private final String telemetryTraceId;

  @Nullable
  private final String telemetryParentSpanId;

  public LongRunningTaskInput(@NotNull String longRunningTaskId, @Nullable String telemetryTraceId, @Nullable String telemetryParentSpanId) {
    this.longRunningTaskId = longRunningTaskId;
    this.telemetryTraceId = telemetryTraceId;
    this.telemetryParentSpanId = telemetryParentSpanId;
  }

  @NotNull
  public String getLongRunningTaskId() {
    return longRunningTaskId;
  }

  @Nullable
  public String getTelemetryTraceId() {
    return telemetryTraceId;
  }

  @Nullable
  public String getTelemetryParentSpanId() {
    return telemetryParentSpanId;
  }

  @Override
  public String toString() {
    return "LongRunningTaskInput{" +
           "taskId='" + longRunningTaskId + '\'' +
           ", traceId='" + telemetryTraceId + '\'' +
           ", spanId='" + telemetryParentSpanId + '\'' +
           '}';
  }
}
