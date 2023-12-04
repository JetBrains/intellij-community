// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Checks if words are correct in the context of Project.
 */
public interface DictionaryChecker {
  ExtensionPointName<DictionaryChecker> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.dictionary.checker");

  boolean isCorrect(@NotNull Project project, @NotNull String word);
}
