// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.spellchecker;

import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface MnemonicsTokenizer {
  boolean hasMnemonics(@NotNull String propertyValue);

  void tokenize(@NotNull PropertyValueImpl element, @NotNull TokenConsumer consumer);

  default Set<Character> ignoredCharacters() {
    return Set.of();
  }
}
