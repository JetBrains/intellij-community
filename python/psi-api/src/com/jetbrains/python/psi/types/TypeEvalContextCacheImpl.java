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

import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Caches context by their constraints (to prevent context cache loss). Flushes cache every PSI change or low memory conditions.
 * Class is thread safe.
 * See {@link #getContext(TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
final class TypeEvalContextCacheImpl implements TypeEvalContextCache {

  @NotNull
  private static final Function<TypeEvalContext, TypeEvalContext> VALUE_PROVIDER = new MyValueProvider();
  @NotNull
  private final TypeEvalContextBasedCache<TypeEvalContext> myCache;

  TypeEvalContextCacheImpl(@NotNull final CachedValuesManager manager) {
    myCache = new TypeEvalContextBasedCache<>(manager, VALUE_PROVIDER);
  }


  @NotNull
  @Override
  public TypeEvalContext getContext(@NotNull final TypeEvalContext standard) {
    return myCache.getValue(standard);
  }

  private static class MyValueProvider implements Function<TypeEvalContext, TypeEvalContext> {
    @Override
    public TypeEvalContext fun(final TypeEvalContext param) {
      // key and value are both context here. If no context stored, then key is stored. Old one is returned otherwise to cache.
      return param;
    }
  }
}
