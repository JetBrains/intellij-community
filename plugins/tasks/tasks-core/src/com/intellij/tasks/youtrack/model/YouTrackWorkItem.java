// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack.model;

import org.jetbrains.annotations.NotNull;

/**
 * @noinspection unused, FieldCanBeLocal
 */
public class YouTrackWorkItem {
  /** @noinspection FieldMayBeStatic*/
  private final boolean usesMarkdown = true;
  private final String text;
  private final Duration duration;

  public YouTrackWorkItem(@NotNull String text, int durationInMinutes) {
    this.text = text;
    duration = new Duration(durationInMinutes);
  }

  public @NotNull String getText() {
    return text;
  }

  public @NotNull Duration getDuration() {
    return duration;
  }

  private record Duration(int minutes) {
  }
}
