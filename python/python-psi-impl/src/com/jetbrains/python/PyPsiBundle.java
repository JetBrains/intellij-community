// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.DynamicBundle;
import com.jetbrains.python.inspections.PyInspectionMessages;
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class PyPsiBundle extends DynamicBundle {
  public static final @NonNls String BUNDLE = "messages.PyPsiBundle";
  public static final PyPsiBundle INSTANCE = new PyPsiBundle();

  private PyPsiBundle() { super(BUNDLE); }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (isMovedToSyntaxCore(key)) {
      return PySyntaxCoreBundle.message(key, params);
    }
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    if (isMovedToSyntaxCore(key)) {
      return PySyntaxCoreBundle.messagePointer(key, params);
    }
    return INSTANCE.getLazyMessage(key, params);
  }

  private static boolean isMovedToSyntaxCore(@NotNull String key) {
    return !INSTANCE.containsKey(key) && PySyntaxCoreBundle.INSTANCE.containsKey(key);
  }

  /**
   * Builds a {@link ProblemMessage} from a bundle entry whose template wraps code-like spans with
   * backticks (see {@link PyInspectionMessages}). Description goes to the Problems view; tooltip goes
   * to the editor hover.
   */
  public static @NotNull ProblemMessage problemMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                        Object @NotNull ... params) {
    return PyInspectionMessages.bundleMessage(INSTANCE, key, params);
  }
}
