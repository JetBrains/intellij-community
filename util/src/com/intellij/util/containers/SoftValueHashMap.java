/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import gnu.trove.THashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class SoftValueHashMap<K,V> implements Map<K,V>{
  private THashMap myMap;
  private ReferenceQueue<MyReference<K,V>> myQueue = new ReferenceQueue<MyReference<K,V>>();

  private static class MyReference<K,V> extends SoftReference<V> {
    final K key;
    public MyReference(K key, V referent, ReferenceQueue q) {
      super(referent, (ReferenceQueue<? super V>)q);
      this.key = key;
    }
  }

  public SoftValueHashMap() {
    myMap = new THashMap();
  }

  private void processQueue() {
    //int count = 0;
    while(true){
      MyReference<K,V> ref = (MyReference<K,V>)myQueue.poll();
      if (ref == null) {
        //if (count > 0){
        //  System.out.println("SoftValueHashMap: " + count + " references have been collected");
        //}
        return;
      }
      if (myMap.get(ref.key) == ref){
        myMap.remove(ref.key);
      }
      //count++;
    }
  }

  public V get(Object key) {
    MyReference<K,V> ref = (MyReference<K,V>)myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  public V put(K key, V value) {
    processQueue();
    MyReference<K,V> oldRef = (MyReference<K,V>)myMap.put(key, new MyReference<K,V>(key, value, myQueue));
    return oldRef != null ? (V)oldRef.get() : null;
  }

  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = (MyReference<K,V>)myMap.remove(key);
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

  public Set<Map.Entry<K, V>> entrySet() {
    throw new RuntimeException("method not implemented");
  }
}