/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.util.containers;

import gnu.trove.THashMap;

import java.util.Map;

/**
 * @author peter
 */
public abstract class FactoryMap<T,V> {
  static final Object NULL = new Object();
  private final Map<T,V> myMap = new THashMap<T, V>();

  protected abstract V create(T key);

  public final V get(T key) {
    V v = myMap.get(getKey(key));
    if (v == null) {
      v = create(key);
      myMap.put(getKey(key), v == null ? (V)NULL : v);
    }
    return v == NULL ? null : v;
  }

  static <T> T getKey(final T key) {
    return key == null ? (T)NULL : key;
  }

  public final boolean containsKey(T key) {
    return myMap.containsKey(getKey(key));
  }

  public void put(T key, V value) {
    myMap.put(key, value);
  }
}
