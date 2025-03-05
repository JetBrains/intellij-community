// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @noinspection unused
 */
public class YouTrackCommandList {
  public static final String DEFAULT_FIELDS = "suggestions(option)";

  private List<Suggestion> suggestions;

  public @NotNull List<Suggestion> getSuggestions() {
    return suggestions;
  }


  public static class Suggestion {
    private String option;

    public @NotNull String getOption() {
      return option;
    }
  }
}
