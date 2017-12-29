// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CustomDictionaryProvider {
  ExtensionPointName<CustomDictionaryProvider> EP_NAME = ExtensionPointName.create("com.intellij.spellchecker.dictionary.customDictionaryProvider");
  
  @Nullable
  Dictionary get(@NotNull String path);

  boolean isApplicable(@NotNull String path);
}
