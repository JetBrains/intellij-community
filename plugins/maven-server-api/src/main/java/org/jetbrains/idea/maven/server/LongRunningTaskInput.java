// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class LongRunningTaskInput implements Serializable {

  private final @NotNull String longRunningTaskId;
  private final @NotNull TelemetryContext telemetryContext;

  public LongRunningTaskInput(@NotNull String longRunningTaskId, @NotNull TelemetryContext telemetryContext) {
    this.longRunningTaskId = longRunningTaskId;
    this.telemetryContext = telemetryContext;
  }

  public @NotNull String getLongRunningTaskId() {
    return longRunningTaskId;
  }

  public @NotNull TelemetryContext getTelemetryContext() {
    return telemetryContext;
  }

  @Override
  public String toString() {
    return "LongRunningTaskInput{" +
           "longRunningTaskId='" + longRunningTaskId + '\'' +
           ", telemetryContext=" + telemetryContext +
           '}';
  }
}
