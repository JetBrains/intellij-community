// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.terminal;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
final class PyTerminalBundle {
  private static final @NonNls String BUNDLE = "messages.PyTerminalBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(PyTerminalBundle.class, BUNDLE);

  private PyTerminalBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
