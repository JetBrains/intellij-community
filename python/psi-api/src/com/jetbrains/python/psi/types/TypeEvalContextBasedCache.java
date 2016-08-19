/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.intellij.psi.util.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Engine to cache something in map, where {@link TypeEvalContext} is used as key.
 * This cache is weak-based (no memory leaks), thread safe and purges on any PSI change.
 *
 * @author Ilya.Kazakevich
 */
public final class TypeEvalContextBasedCache<T> {
  /**
   * Lock to sync
   */
  @NotNull
  private final Object myLock = new Object();
  @NotNull
  private final CachedValue<Map<TypeEvalConstraints, T>> myCachedMapStorage;

  @NotNull
  private final Function<TypeEvalContext, T> myProvider;

  /**
   * @param manager       Cache manager to be used to store cache
   * @param valueProvider engine to create value based on context.
   */
  public TypeEvalContextBasedCache(@NotNull final CachedValuesManager manager,
                                   @NotNull final Function<TypeEvalContext, T> valueProvider) {
    myCachedMapStorage = manager.createCachedValue(new MapCreator<T>(), false);
    myProvider = valueProvider;
  }

  /**
   * Returns value (executes provider to obtain new if no any and stores it in cache)
   * @param context to be used as key
   * @return value
   */
  @NotNull
  public T getValue(@NotNull final TypeEvalContext context) {

    // Map is not thread safe, and "getValue" is not atomic. I do not want several maps to be created.
    synchronized (myLock) {
      final Map<TypeEvalConstraints, T> map = myCachedMapStorage.getValue();
      T value = map.get(context.getConstraints());
      if (value != null) {
        return value;
      }
      // This is the same value, semantically: value for context-key
      //noinspection ReuseOfLocalVariable
      value = myProvider.fun(context);
      map.put(context.getConstraints(), value);
      return value;
    }
  }

  /**
   * Provider that creates map to store cache. Map depends on PSI modification
   */
  private static final class MapCreator<T> implements CachedValueProvider<Map<TypeEvalConstraints, T>> {
    @Nullable
    @Override
    public Result<Map<TypeEvalConstraints, T>> compute() {
      // This method is called if cache is empty. Create new map for it.
      final HashMap<TypeEvalConstraints, T> map = new HashMap<>();
      return new Result<>(map, PsiModificationTracker.MODIFICATION_COUNT);
    }
  }
}
