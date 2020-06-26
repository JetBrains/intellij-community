// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class SvnBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "messages.SvnBundle";

  private static final SvnBundle INSTANCE = new SvnBundle();

  private SvnBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  /**
   * @deprecated use {@link #message(String, Object...)}
   */
  @Deprecated
  @NotNull
  public static String getString(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return message(key);
  }
}
