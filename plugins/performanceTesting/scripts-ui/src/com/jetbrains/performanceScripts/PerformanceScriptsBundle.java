// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class PerformanceScriptsBundle {

  private static final @NonNls String BUNDLE_FQN = "messages.PerformanceScriptsBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(PerformanceScriptsBundle.class, BUNDLE_FQN);

  private PerformanceScriptsBundle() {
  }

  public static @Nls @NotNull String message(
    @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return BUNDLE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(
    @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return BUNDLE.getLazyMessage(key, params);
  }
}
