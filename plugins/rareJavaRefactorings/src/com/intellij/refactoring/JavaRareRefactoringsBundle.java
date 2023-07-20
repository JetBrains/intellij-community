// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class JavaRareRefactoringsBundle {
  private static final @NonNls String BUNDLE = "messages.JavaRareRefactoringsBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(JavaRareRefactoringsBundle.class, BUNDLE);

  private JavaRareRefactoringsBundle() {
  }

  public static @NotNull @Nls
  String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
