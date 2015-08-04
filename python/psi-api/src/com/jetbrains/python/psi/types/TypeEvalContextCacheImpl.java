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
package com.jetbrains.python.psi.types;

import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches context by their constraints (to prevent context cache loss). Flushes cache every PSI change or low memory conditions.
 * Class is thread safe.
 * See {@link #getContext(TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
final class TypeEvalContextCacheImpl implements TypeEvalContextCache {

  /**
   * Producer to create map to store cache
   */
  @NotNull
  private static final MapCreator MAP_CREATOR = new MapCreator();

  /**
   * Lock to sync
   */
  @NotNull
  private final Object myLock = new Object();

  @NotNull
  private final CachedValue<Map<TypeEvalConstraints, TypeEvalContext>> myCachedMapStorage;


  TypeEvalContextCacheImpl(@NotNull final CachedValuesManager manager) {
    myCachedMapStorage = manager.createCachedValue(MAP_CREATOR, false);
  }


  @NotNull
  @Override
  public TypeEvalContext getContext(@NotNull final TypeEvalContext standard) {

    // Map is not thread safe, and "getValue" is not atomic. I do not want several maps to be created.
    synchronized (myLock) {
      final Map<TypeEvalConstraints, TypeEvalContext> map = myCachedMapStorage.getValue();
      final TypeEvalContext context = map.get(standard.getConstraints());
      if (context != null) {
        return context;
      }
      map.put(standard.getConstraints(), standard);
      return standard;
    }
  }

  /**
   * Provider that creates map to store cache. Map depends on PSI modification
   */
  private static final class MapCreator implements CachedValueProvider<Map<TypeEvalConstraints, TypeEvalContext>> {
    @Nullable
    @Override
    public Result<Map<TypeEvalConstraints, TypeEvalContext>> compute() {
      // This method is called if cache is empty. Create new map for it.
      final HashMap<TypeEvalConstraints, TypeEvalContext> map = new HashMap<TypeEvalConstraints, TypeEvalContext>();
      return new Result<Map<TypeEvalConstraints, TypeEvalContext>>(map, PsiModificationTracker.MODIFICATION_COUNT);
    }
  }
}
