// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Provides dictionaries that are always project-bound, e.g. provide names of libraries used in the Project.
 */
public interface DictionaryCheckerProvider {
  ExtensionPointName<DictionaryCheckerProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.dictionary.checker");

  @NotNull DictionaryChecker getChecker(@NotNull Project project);
}
