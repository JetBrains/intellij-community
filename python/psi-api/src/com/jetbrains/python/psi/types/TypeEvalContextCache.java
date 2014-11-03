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

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiModificationTracker.SERVICE;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches context by their constraints (to prevent context cache loss). Flushes cache every PSI change.
 * Class is thread safe.
 * See {@link #getContext(com.intellij.openapi.project.Project, TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
class TypeEvalContextCache {
  /**
   * Cache itself.
   */
  @NotNull
  private final Map<TypeEvalConstraints, TypeEvalContext> myCache = new HashMap<TypeEvalConstraints, TypeEvalContext>();
  /**
   * Current PSI modification count
   */
  private long myModificationCount = -1;
  /**
   * Lock to sync
   */
  @NotNull
  private final Object myLock = new Object();


  /**
   * Returns context from cache (if exist) or returns the one you provided (and puts it into cache).
   * To use this method, do the following:
   * <ol>
   * <li>Instantiate {@link com.jetbrains.python.psi.types.TypeEvalContext} you want to use</li>
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
    final PsiModificationTracker tracker = SERVICE.getInstance(project);
    synchronized (myLock) {
      final long currentCount = tracker.getOutOfCodeBlockModificationCount();
      if (currentCount == myModificationCount) {
        // Cache is valid, use it
        final TypeEvalContext valueFromCache = myCache.get(standard.getConstraints());
        if (valueFromCache != null) {
          // We have element in cache, return it
          return valueFromCache;
        }
      }
      else {
        // Cache is invalid, flush it and store current count
        myCache.clear();
        myModificationCount = currentCount;
      }
      // We do not have value in cache (or cache is invalid), put it
      myCache.put(standard.getConstraints(), standard);
      return standard;
    }
  }
}
