// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MavenEventType {
  SESSION_STARTED("SessionStarted"),
  SESSION_ENDED("SessionEnded"),
  PROJECT_STARTED("ProjectStarted"),
  MOJO_STARTED("MojoStarted"),
  MOJO_SUCCEEDED("MojoSucceeded"),
  MOJO_FAILED("MojoFailed"),
  MOJO_SKIPPED("MojoSkipped"),
  PROJECT_SUCCEEDED("ProjectSucceeded"),
  PROJECT_SKIPPED("ProjectSkipped"),
  PROJECT_FAILED("ProjectFailed"),
  ARTIFACT_RESOLVED("ARTIFACT_RESOLVED"),
  ARTIFACT_DOWNLOADING("ARTIFACT_DOWNLOADING");

  public final String eventName;

  MavenEventType(String eventName) {
    this.eventName = eventName;
  }

  private static final Map<String, MavenEventType> eventsByName = Arrays.stream(values())
    .collect(Collectors.toMap(v -> v.eventName, Function.identity()));

  public static @Nullable MavenEventType valueByName(@NotNull String eventName) {
    return eventsByName.get(eventName);
  }
}
