// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.util.SofterReference;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

final class SofterCache<T,V> {
  private final Function<? super T, ? extends V> myValueProvider;
  private SofterReference<ConcurrentMap<T, V>> myCache;

  SofterCache(Function<? super T, @NotNull ? extends V> valueProvider) {
    myValueProvider = valueProvider;
  }

  public void clearCache() {
    myCache = null;
  }

  public @NotNull V getCachedValue(T key) {
    SofterReference<ConcurrentMap<T, V>> ref = myCache;
    ConcurrentMap<T, V> map = ref == null ? null : ref.get();
    if (map == null) {
      map = new ConcurrentHashMap<>();
      myCache = new SofterReference<>(map);
    }
    return map.computeIfAbsent(key, myValueProvider);
  }
}
