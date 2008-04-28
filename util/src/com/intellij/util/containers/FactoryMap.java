/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.HashSet;

/**
 * @author peter
 */
public abstract class FactoryMap<K,V> implements Map<K, V> {
  static final Object NULL = new Object() {
    @NonNls
    public String toString() {
      return "NULL";
    }
  };
  protected final Map<K,V> myMap = createMap();

  protected Map<K, V> createMap() {
    return new THashMap<K, V>();
  }

  @Nullable
  protected abstract V create(K key);
  
  @Nullable
  public V get(Object key) {
    V value = myMap.get(getKey(key));
    if (value == null) {
      value = create((K)key);
      myMap.put((K)getKey(key), value == null ? (V)NULL : value);
    }
    return value == NULL ? null : value;
  }

  private static <K> K getKey(final K key) {
    return key == null ? (K)NULL : key;
  }

  public final boolean containsKey(Object key) {
    return myMap.containsKey(getKey(key));
  }

  public V put(K key, V value) {
    return myMap.put(getKey(key), value == null ? (V)NULL : value);
  }

  public V remove(Object key) {
    return myMap.remove(key);
  }

  public Set<K> keySet() {
    final Set<K> ts = myMap.keySet();
    if (ts.contains(NULL)) {
      final HashSet<K> hashSet = new HashSet<K>(ts);
      hashSet.remove(NULL);
      hashSet.add(null);
      return hashSet;
    }
    return ts;
  }

  public Collection<V> notNullValues() {
    final Collection<V> values = new ArrayList<V>(myMap.values());
    for (Iterator<V> iterator = values.iterator(); iterator.hasNext();) {
      if (iterator.next() == NULL) {
        iterator.remove();
      }
    }
    return values;
  }

  public void clear() {
    myMap.clear();
  }

  public int size() {
    return myMap.size();
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  public boolean containsValue(final Object value) {
    return myMap.containsValue(value);
  }

  public void putAll(final Map<? extends K, ? extends V> m) {
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  public Collection<V> values() {
    return myMap.values();
  }

  public Set<Entry<K, V>> entrySet() {
    return myMap.entrySet();
  }
}
