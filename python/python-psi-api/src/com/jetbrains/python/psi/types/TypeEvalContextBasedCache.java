// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * Engine to cache something in map, where {@link TypeEvalContext} is used as key.
 * This cache is weak-based (no memory leaks), thread safe and purges on any PSI change.
 *
 * @author Ilya.Kazakevich
 */
public final class TypeEvalContextBasedCache<T> {
  private final @NotNull CachedValue<ConcurrentMap<TypeEvalConstraints, T>> myCachedMapStorage;

  private final @NotNull Function<? super TypeEvalContext, ? extends T> myProvider;

  /**
   * @param manager       Cache manager to be used to store cache
   * @param valueProvider engine to create value based on context.
   */
  public TypeEvalContextBasedCache(final @NotNull CachedValuesManager manager,
                                   final @NotNull Function<? super TypeEvalContext, ? extends T> valueProvider) {
    myCachedMapStorage = manager.createCachedValue(new MapCreator<>(), false);
    myProvider = valueProvider;
  }

  /**
   * Returns value (executes provider to obtain new if no any and stores it in cache).
   * It is better to run this method under read action to make sure PSI not modified in the middle of its execution
   *
   * @param context to be used as key
   * @return value
   */
  public @NotNull T getValue(final @NotNull TypeEvalContext context) {

    // map is thread safe but not atomic nor getValue() is, so in worst case several threads may produce same result
    // myProvider.fun should never be launched under lock to prevent deadlocks like PY-24300 and PY-24625
    // both explicit locking and computeIfAbsent leads to deadlock
    final ConcurrentMap<TypeEvalConstraints, T> map = myCachedMapStorage.getValue();
    final TypeEvalConstraints key = context.getConstraints();
    final T value = map.get(key);
    if (value != null) {
      return value;
    }
    final T newValue = myProvider.fun(context);
    T oldValue =
      map.putIfAbsent(key, newValue);// ConcurrentMap guarantees happens-before so from this moment get() should work in other threads
    return oldValue == null ? newValue : oldValue;
  }

  /**
   * Provider that creates map to store cache. Map depends on PSI modification
   */
  private static final class MapCreator<T> implements CachedValueProvider<ConcurrentMap<TypeEvalConstraints, T>> {
    @Override
    public @NotNull Result<ConcurrentMap<TypeEvalConstraints, T>> compute() {
      // This method is called if cache is empty. Create new map for it.
      // Concurrent map allows several threads to call get and put, so it is thread safe but not atomic
      final ConcurrentMap<TypeEvalConstraints, T> map = ContainerUtil.createConcurrentSoftValueMap();
      return new Result<>(map, PsiModificationTracker.MODIFICATION_COUNT);
    }
  }
}
