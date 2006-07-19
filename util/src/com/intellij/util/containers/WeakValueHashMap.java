/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public final class WeakValueHashMap<K,V> implements Map<K,V>{
  private HashMap<K,MyReference<K,V>> myMap;
  private ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class MyReference<K,T> extends WeakReference<T> {
    final K key;

    public MyReference(K key, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }
  }

  public WeakValueHashMap() {
    myMap = new HashMap<K, MyReference<K,V>>();
  }

  private void processQueue() {
    while(true){
      MyReference ref = (MyReference)myQueue.poll();
      if (ref == null) {
        return;
      }
      if (myMap.get(ref.key) == ref){
        myMap.remove(ref.key);
      }
    }
  }

  public V get(Object key) {
    MyReference<K,V> ref = myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  public V put(K key, V value) {
    processQueue();
    MyReference<K,V> oldRef = myMap.put(key, new MyReference<K,V>(key, value, myQueue));
    return oldRef != null ? oldRef.get() : null;
  }

  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = myMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    throw new RuntimeException("method not implemented");
  }

  public void clear() {
    myMap.clear();
  }

  public int size() {
    return myMap.size(); //?
  }

  public boolean isEmpty() {
    return myMap.isEmpty(); //?
  }

  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  public boolean containsValue(Object value) {
    throw new RuntimeException("method not implemented");
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyReference<K, V>> refs = myMap.values();
    for (MyReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  public Set<Entry<K, V>> entrySet() {
    throw new RuntimeException("method not implemented");
  }
}