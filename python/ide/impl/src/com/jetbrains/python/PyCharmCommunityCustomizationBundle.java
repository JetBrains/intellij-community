// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class PyCharmCommunityCustomizationBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.PyCharmCommunityCustomizationBundle";
  private static final PyCharmCommunityCustomizationBundle INSTANCE = new PyCharmCommunityCustomizationBundle();

  private PyCharmCommunityCustomizationBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
