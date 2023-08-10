// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @noinspection unused
 */
public class YouTrackCommandList {
  public static final String DEFAULT_FIELDS = "suggestions(option)";

  private List<Suggestion> suggestions;

  @NotNull
  public List<Suggestion> getSuggestions() {
    return suggestions;
  }


  public static class Suggestion {
    private String option;

    @NotNull
    public String getOption() {
      return option;
    }
  }
}
