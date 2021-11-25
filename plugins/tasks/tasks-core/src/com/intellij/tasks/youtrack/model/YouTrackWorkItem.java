// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public String getText() {
    return text;
  }

  @NotNull
  public Duration getDuration() {
    return duration;
  }

  private static class Duration {
    private final int minutes;

    private Duration(int minutes) {
      this.minutes = minutes;
    }

    private int getMinutes() {
      return minutes;
    }
  }
}
