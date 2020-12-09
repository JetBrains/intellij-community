// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface Loader {
  void load(@NotNull Consumer<String> consumer);

  String getName();
}
