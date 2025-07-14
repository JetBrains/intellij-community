// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * Caches context by their constraints (to prevent context cache loss). Flushes cache every PSI change or low memory conditions.
 * Class is thread safe.
 * See {@link #getContext(TypeEvalContext)}
 *
 * @author Ilya.Kazakevich
 */
final class TypeEvalContextCacheImpl implements TypeEvalContextCache {
  private final @NotNull CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> myCachedMapStorage;
  private final @NotNull CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> myLibrariesCachedMapStorage;

  TypeEvalContextCacheImpl(@NotNull Project project) {
    myCachedMapStorage = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<>() {
      @Override
      public @NotNull CachedValueProvider.Result<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> compute() {
        // This method is called if cache is empty. Create new map for it.
        // Concurrent map allows several threads to call get and put, so it is thread safe but not atomic
        final ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = ContainerUtil.createConcurrentSoftValueMap();
        return new CachedValueProvider.Result<>(map, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });

    myLibrariesCachedMapStorage = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<>() {
      @Override
      public @NotNull CachedValueProvider.Result<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> compute() {
        // This method is called if cache is empty. Create new map for it.
        // Concurrent map allows several threads to call get and put, so it is thread safe but not atomic
        final ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = ContainerUtil.createConcurrentSoftValueMap();
        return new CachedValueProvider.Result<>(map, PyLibraryModificationTracker.Companion.getInstance(project));
      }
    });
  }

  private static TypeEvalContext retrieveFromStorage(@NotNull TypeEvalContext standard,
                                                     CachedValue<ConcurrentMap<TypeEvalConstraints, TypeEvalContext>> storage) {
    // map is thread safe but not atomic nor getValue() is, so in worst case several threads may produce same result
    // both explicit locking and computeIfAbsent leads to deadlock
    final TypeEvalConstraints key = standard.getConstraints();
    final ConcurrentMap<TypeEvalConstraints, TypeEvalContext> map = storage.getValue();
    final TypeEvalContext cachedContext = map.get(key);
    if (cachedContext != null) {
      return cachedContext;
    }
    TypeEvalContext oldValue =
      map.putIfAbsent(key, standard);// ConcurrentMap guarantees happens-before so from this moment get() should work in other threads
    return oldValue == null ? standard : oldValue;
  }

  @Override
  public @NotNull TypeEvalContext getContext(@NotNull TypeEvalContext standard) {
    return retrieveFromStorage(standard, myCachedMapStorage);
  }

  @Override
  public @NotNull TypeEvalContext getLibraryContext(@NotNull TypeEvalContext standard) {
    return retrieveFromStorage(standard, myLibrariesCachedMapStorage);
  }
}
