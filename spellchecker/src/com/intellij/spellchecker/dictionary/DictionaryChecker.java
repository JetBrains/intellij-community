// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import org.jetbrains.annotations.NotNull;

/**
 * Checks if words are correct in the context of Project.
 *
 * @see DictionaryCheckerProvider
 */
public interface DictionaryChecker {
  boolean isCorrect(@NotNull String word);
}
