// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.shared;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * {@link ResourceBundle}/localization utils for the lombok plugin.
 */
public final class LombokBundle {
  public static final @NonNls String PATH_TO_BUNDLE = "messages.LombokBundle";
  private static final DynamicBundle ourInstance = new DynamicBundle(LombokBundle.class, PATH_TO_BUNDLE);

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return ourInstance.getLazyMessage(key, params);
  }
}
