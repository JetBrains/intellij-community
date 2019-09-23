// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
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

  TypeEvalContextCacheImpl(@NotNull Project project) {
    myCache = new TypeEvalContextBasedCache<>(CachedValuesManager.getManager(project), VALUE_PROVIDER);
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
