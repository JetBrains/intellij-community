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
package com.intellij.util.xml.impl;

import com.intellij.util.NotNullFunction;
import com.intellij.util.SofterReference;
import com.intellij.util.containers.ContainerUtil;

import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class SofterCache<T,V> {
  private final NotNullFunction<T,V> myValueProvider;
  private SofterReference<ConcurrentMap<T, V>> myCache;

  public SofterCache(NotNullFunction<T, V> valueProvider) {
    myValueProvider = valueProvider;
  }

  public static <T, V> SofterCache<T, V> create(NotNullFunction<T, V> valueProvider) {
    return new SofterCache<>(valueProvider);
  }

  public void clearCache() {
    myCache = null;
  }

  public V getCachedValue(T key) {
    SofterReference<ConcurrentMap<T, V>> ref = myCache;
    ConcurrentMap<T, V> map = ref == null ? null : ref.get();
    if (map == null) {
      myCache = new SofterReference<>(map = ContainerUtil.newConcurrentMap());
    }
    V value = map.get(key);
    if (value == null) {
      map.put(key, value = myValueProvider.fun(key));
    }
    return value;
  }


}
