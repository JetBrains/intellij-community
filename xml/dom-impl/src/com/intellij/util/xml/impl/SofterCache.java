// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.util.NotNullFunction;
import com.intellij.util.SofterReference;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class SofterCache<T,V> {
  private final NotNullFunction<? super T, ? extends V> myValueProvider;
  private SofterReference<ConcurrentMap<T, V>> myCache;

  public SofterCache(NotNullFunction<? super T, ? extends V> valueProvider) {
    myValueProvider = valueProvider;
  }

  public static <T, V> SofterCache<T, V> create(NotNullFunction<? super T, ? extends V> valueProvider) {
    return new SofterCache<>(valueProvider);
  }

  public void clearCache() {
    myCache = null;
  }

  public V getCachedValue(T key) {
    SofterReference<ConcurrentMap<T, V>> ref = myCache;
    ConcurrentMap<T, V> map = ref == null ? null : ref.get();
    if (map == null) {
      myCache = new SofterReference<>(map = new ConcurrentHashMap<>());
    }
    V value = map.get(key);
    if (value == null) {
      map.put(key, value = myValueProvider.fun(key));
    }
    return value;
  }


}
