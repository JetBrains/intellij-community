/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
    throw new RuntimeException("method not implemented");
  }

  public Set<Entry<K, V>> entrySet() {
    throw new RuntimeException("method not implemented");
  }
}