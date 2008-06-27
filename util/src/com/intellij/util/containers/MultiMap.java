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

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class MultiMap<K, V> {

  private final Map<K, Collection<V>> myMap;

  public MultiMap() {
    myMap = createMap();
  }

  protected Map<K, Collection<V>> createMap() {
    return new HashMap<K, Collection<V>>();
  }

  protected Collection<V> createCollection() {
    return new ArrayList<V>();
  }

  public void putValue(K key, V value) {
    Collection<V> list = myMap.get(key);
    if (list == null) {
      list = createCollection();
      myMap.put(key, list);
    }
    list.add(value);
  }

  public boolean isEmpty() {
    for(Collection<V> valueList: myMap.values()) {
      if (!valueList.isEmpty()) {
        return false;
      }
    }
    return true;    
  }

  public boolean containsScalarValue(V value) {
    for(Collection<V> valueList: myMap.values()) {
      if (valueList.contains(value)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Collection<V> get(final K key) {
    final Collection<V> collection = myMap.get(key);
    return collection == null ? Collections.<V>emptyList() : collection;
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public int size() {
    return myMap.size();
  }

  public void put(final K key, final Collection<V> values) {
    myMap.put(key, values);
  }

  public void removeValue(final K key, final V value) {
    final Collection<V> values = myMap.get(key);
    values.remove(value);
    if (values.isEmpty()) {
      myMap.remove(key);
    }
  }

  public Collection<? extends V> values() {
    ArrayList<V> result = new ArrayList<V>();
    for (Collection<V> vs : myMap.values()) {
      result.addAll(vs);
    }
    return result;
  }
}
