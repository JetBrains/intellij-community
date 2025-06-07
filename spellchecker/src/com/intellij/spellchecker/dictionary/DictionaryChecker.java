// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks if words are correct in the context of the project.
 */
public interface DictionaryChecker {
  ExtensionPointName<DictionaryChecker> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.dictionary.checker");

  boolean isCorrect(@NotNull Project project, @NotNull String word);
}

class ProjectNameDictionaryChecker implements DictionaryChecker {
  private final Pattern splitWordsRegex = Pattern.compile("[._-]|\\s|\\d");
  private final Key<Set<String>> projectWordsKey = Key.create("PROJECT_SPELLING_WORDS");

  @Override
  public boolean isCorrect(@NotNull Project project, @NotNull String word) {
    Set<String> words = project.getUserData(projectWordsKey);
    if (words != null) {
      return words.contains(word.toLowerCase(Locale.ROOT));
    }

    words = Set.copyOf(Arrays.stream(splitWordsRegex.split(project.getName()))
                         .map(w -> w.trim().toLowerCase(Locale.ROOT))
                         .filter(w -> w.length() > 3)
                         .collect(Collectors.toSet()));

    project.putUserData(projectWordsKey, words);

    return words.contains(word.toLowerCase(Locale.ROOT));
  }
}