// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.engine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Transformation {

  public @Nullable String transform(@Nullable String word) {
    if (word == null) return null;
    word = word.trim();
    if (word.length() < 3) {
      return null;
    }

    return StringUtil.toLowerCase(word);
  }

  public @Nullable Set<String> transform(@Nullable Collection<String> words) {
    if (words == null || words.isEmpty()) {
      return null;
    }
    Set<String> result = new HashSet<>();
    for (String word : words) {
      String transformed = transform(word);
      if (transformed != null) {
        result.add(transformed);
      }
    }
    return result;
  }
}
