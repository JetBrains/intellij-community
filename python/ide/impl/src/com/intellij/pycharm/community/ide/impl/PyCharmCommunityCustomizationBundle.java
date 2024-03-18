// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class PyCharmCommunityCustomizationBundle {
  private static final @NonNls String BUNDLE = "messages.PyCharmCommunityCustomizationBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(PyCharmCommunityCustomizationBundle.class, BUNDLE);

  private PyCharmCommunityCustomizationBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
