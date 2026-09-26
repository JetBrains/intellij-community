// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes;

import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class LazySuggestions {
  private List<String> suggestions;
  private boolean processed;
  protected final String typo;

  public LazySuggestions(String typo, @Nullable Set<String> suggestions) {
    this.typo = typo;
    if (suggestions != null) {
      this.suggestions = List.copyOf(suggestions);
      this.processed = !this.suggestions.isEmpty();
    }
  }

  public @NotNull List<String> getSuggestions(Project project) {
    if (!processed) {
      suggestions = SpellCheckerManager.getInstance(project).getSuggestions(typo);
      processed = true;
    }
    return suggestions;
  }
}
