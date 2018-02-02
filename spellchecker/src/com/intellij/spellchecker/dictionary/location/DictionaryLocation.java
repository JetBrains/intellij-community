// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary.location;

import org.jetbrains.annotations.NotNull;

public interface DictionaryLocation {
  @NotNull
  String getName();

  void findAndAddNewDictionary();
}
