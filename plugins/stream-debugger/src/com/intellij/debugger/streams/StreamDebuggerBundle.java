// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class StreamDebuggerBundle extends DynamicBundle {
  private static final String BUNDLE = "messages.StreamDebuggerBundle";
  private static final StreamDebuggerBundle INSTANCE = new StreamDebuggerBundle();

  private StreamDebuggerBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}