// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

final class PyWelcomeBundle {
  private static final @NonNls String BUNDLE = "messages.PyWelcomeBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(PyWelcomeBundle.class, BUNDLE);

  private PyWelcomeBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
