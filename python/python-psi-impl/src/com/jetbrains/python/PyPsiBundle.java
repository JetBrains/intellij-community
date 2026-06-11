/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
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
