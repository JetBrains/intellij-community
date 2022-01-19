// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CustomDictionaryProvider {
  ExtensionPointName<CustomDictionaryProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.dictionary.customDictionaryProvider");

  @Nullable
  Dictionary get(@NotNull String path);

  boolean isApplicable(@NotNull String path);

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  default String getDictionaryType() {
    return "";
  }
}
