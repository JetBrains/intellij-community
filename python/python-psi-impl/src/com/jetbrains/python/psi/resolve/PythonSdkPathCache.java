// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The resolve cache of the roots of one interpreter in one project, and its {@link PyBuiltinCache}. The project keeps one
 * per interpreter until a snapshot or the roots of an interpreter change, see {@link #getInstance(Project, PythonInterpreter)}.
 */
public final class PythonSdkPathCache extends UserDataHolderBase {
  private static final Key<CachedValue<Map<PythonInterpreter, PythonSdkPathCache>>> KEY = Key.create("PythonSdkPathCaches");

  public static @NotNull PythonSdkPathCache getInstance(@NotNull Project project, @NotNull PythonInterpreter interpreter) {
    final Map<PythonInterpreter, PythonSdkPathCache> caches = CachedValuesManager.getManager(project).getCachedValue(
      project, KEY, () -> CachedValueProvider.Result.create(
        new ConcurrentHashMap<>(), PyResolveCachesInvalidator.getInstance(project).getModificationTracker()), false);
    return caches.computeIfAbsent(interpreter, it -> new PythonSdkPathCache(project, it));
  }

  private final Project myProject;
  private final PythonInterpreter myInterpreter;
  private final AtomicReference<PyBuiltinCache> myBuiltins = new AtomicReference<>();

  private PythonSdkPathCache(final @NotNull Project project, final @NotNull PythonInterpreter interpreter) {
    myProject = project;
    myInterpreter = interpreter;
  }

  /** The resolve cache of the roots of the interpreter, for a file outside every module. */
  public @NotNull PythonPathCache getPathCache() {
    return CachedValuesManager.getManager(myProject).getCachedValue(this, () -> CachedValueProvider.Result.create(
      new PythonPathCache(), PyResolveCachesInvalidator.getInstance(myProject).getPathCacheDependencies()));
  }

  public @NotNull PyBuiltinCache getBuiltins() {
    while (true) {
      PyBuiltinCache pyBuiltinCache = myBuiltins.get();
      if (pyBuiltinCache == null || !pyBuiltinCache.isValid()) {
        PyBuiltinCache newCache = new PyBuiltinCache(myProject, myInterpreter);
        if (myBuiltins.compareAndSet(pyBuiltinCache, newCache)) {
          return newCache;
        }
      }
      else {
        return pyBuiltinCache;
      }
    }
  }
}
