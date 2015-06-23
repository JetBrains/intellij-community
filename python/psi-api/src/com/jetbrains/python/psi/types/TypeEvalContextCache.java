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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
 * See {@link #getContext(Project, TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
final class TypeEvalContextCache implements CachedValueProvider<Map<TypeEvalConstraints, TypeEvalContext>>, Disposable {
  /**
   * Lock to sync
   */
  @NotNull
  private final Object myLock = new Object();

  /**
   * {@link CachedValue} to store/create map [constraints, context]
   */
  private volatile CachedValue<Map<TypeEvalConstraints, TypeEvalContext>> myCachedMapStorage;

  /**
   * Returns context from cache (if exist) or returns the one you provided (and puts it into cache).
   * To use this method, do the following:
   * <ol>
   * <li>Instantiate {@link TypeEvalContext} you want to use</li>
   * <li>Pass its instance here as argument</li>
   * <li>Use result</li>
   * </ol>
   *
   * @param project  project is required for caching engine
   * @param standard context you want to use. Just instantiate it and pass here.
   * @return context from cache (the one equals by constraints to yours or the one you provided)
   */
  @NotNull
  TypeEvalContext getContext(@NotNull final Project project, @NotNull final TypeEvalContext standard) {

    // Double check here to prevent useless sync
    if (myCachedMapStorage == null) {
      final CachedValuesManager manager = CachedValuesManager.getManager(project);
      synchronized (myLock) { // Create storage if not exists. Should be created at first launch only
        if (myCachedMapStorage == null) {
          myCachedMapStorage = manager.createCachedValue(this, false);
          Disposer.register(project, this); // To nullify property on project close
        }
      }
    }
    // Map is not thread safe nor "getValue" is.
    synchronized (myLock) {
      final Map<TypeEvalConstraints, TypeEvalContext> map = myCachedMapStorage.getValue();
      final TypeEvalContext context = map.get(standard.getConstraints());
      if (context != null) { // Context already in cache, return it
        return context;
      }
      map.put(standard.getConstraints(), standard); // Put this context to cache
      return standard;
    }
  }

  @Nullable
  @Override
  public Result<Map<TypeEvalConstraints, TypeEvalContext>> compute() {
    // This method is called if cache is empty. Create new map for it.
    final HashMap<TypeEvalConstraints, TypeEvalContext> map = new HashMap<TypeEvalConstraints, TypeEvalContext>();
    return new Result<Map<TypeEvalConstraints, TypeEvalContext>>(map, PsiModificationTracker.MODIFICATION_COUNT);
  }

  @Override
  public void dispose() {
    // On project close
    myCachedMapStorage = null;
  }
}
