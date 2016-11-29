/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.references;

import com.intellij.openapi.util.Key;
import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

class AddValueCondition<T> extends PatternCondition<T> {
  private final Key<? extends Set<T>> myKey;

  public AddValueCondition(Key<? extends Set<T>> key) {
    super("AddValue");
    myKey = key;
  }

  public static <T> AddValueCondition<T> create(Key<? extends Set<T>> key) {
    return new AddValueCondition<>(key);
  }

  @Override
  public boolean accepts(@NotNull T value, ProcessingContext context) {
    context.get(myKey).add(value);
    return true;
  }
}