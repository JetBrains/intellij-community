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
package com.jetbrains;

import com.google.common.base.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filters out nullable elements allowing children to filter not-null elements
 *
 * @author Ilya.Kazakevich
 * @deprecated use java 8 primitives instead
 */
@Deprecated(forRemoval = true)
public class NotNullPredicate<T> implements Predicate<T> {
  /**
   * Simply filters nulls
   */
  public static final Predicate<Object> INSTANCE = new NotNullPredicate<>();

  @Override
  public final boolean apply(@Nullable final T input) {
    if (input == null) {
      return false;
    }
    return applyNotNull(input);
  }

  protected boolean applyNotNull(@NotNull final T input) {
    return true;
  }
}
