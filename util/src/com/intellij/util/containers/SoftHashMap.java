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

import com.intellij.openapi.util.Comparing;
import com.intellij.reference.SoftReference;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.lang.ref.ReferenceQueue;
import java.util.*;

public final class SoftHashMap<K,V> extends AbstractMap<K,V> implements Map<K,V> {
  private Map<Key<K>,V> myMap;
  private ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<K>();
  private HardKey<K> myHardKeyInstance = new HardKey<K>(null); // "singleton"
  private Set<Map.Entry<K,V>> entrySet;

  private static interface Key<T>{
    T get();
  }

  private static class SoftKey<T> extends SoftReference<T> implements Key<T>{
    private int myHash;	/* Hashcode of key, stored here since the key may be tossed by the GC */

    private SoftKey(T k, ReferenceQueue<? super T> q) {
      super(k, q);
      myHash = k.hashCode();
    }

    private static <V> SoftKey<V> create(V k, ReferenceQueue<? super V> q) {
      return k != null ? new SoftKey<V>(k, q) : null;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = get();
      Object u = ((Key)o).get();
      if (t == null || u == null) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private static class HardKey<T> implements Key<T>{
    private T myObject;
    private int myHash;

    public HardKey(T object) {
      set(object);
    }

    public T get() {
      return myObject;
    }

    public void set(T object) {
      myObject = object;
      myHash = object == null ? 0 : object.hashCode();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = get();
      Object u = ((Key)o).get();
      if (t == null || u == null) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private void processQueue() {
    SoftKey wk;
    while((wk = (SoftKey)myReferenceQueue.poll()) != null){
      myMap.remove(wk);
    }
  }

  public SoftHashMap(int initialCapacity, float loadFactor) {
    myMap = new THashMap<Key<K>, V>(initialCapacity, loadFactor);
  }

  public SoftHashMap(int initialCapacity) {
    myMap = new THashMap<Key<K>, V>(initialCapacity);
  }

  public SoftHashMap() {
    myMap = new THashMap<Key<K>, V>();
  }

  public SoftHashMap(final TObjectHashingStrategy<K> hashingStrategy) {
    myMap = new THashMap<Key<K>, V>(new TObjectHashingStrategy<Key<K>>() {
      public int computeHashCode(final Key<K> object) {
        return hashingStrategy.computeHashCode(object.get());
      }

      public boolean equals(final Key<K> o1, final Key<K> o2) {
        return hashingStrategy.equals(o1.get(), o2.get());
      }
    } );
  }

  public SoftHashMap(Map<? extends K, ? extends V> t) {
    this(Math.max(2 * t.size(), 11), 0.75f);
    putAll(t);
  }

  public int size() {
    return entrySet().size();
  }

  public boolean isEmpty() {
    return entrySet().isEmpty();
  }

  public boolean containsKey(Object key) {
    // optimization:
    if (key == null){
      return myMap.containsKey(null);
    }
    else{
      myHardKeyInstance.set((K)key);
      boolean result = myMap.containsKey(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return result;
    }
    //return myMap.containsKey(SoftKey.create(key));
  }

  public V get(Object key) {
    //return myMap.get(SoftKey.create(key));
    // optimization!!!
    if (key == null){
      return myMap.get(null);
    }
    else{
      myHardKeyInstance.set((K)key);
      V result = myMap.get(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return result;
    }
  }

  public V put(K key, V value) {
    processQueue();
    Key<K> keyWrapper = SoftKey.create(key, myReferenceQueue);
    return myMap.put(keyWrapper, value);
  }

  public V remove(Object key) {
    processQueue();

    // optimization:
    if (key == null){
      return myMap.remove(null);
    }
    else{
      myHardKeyInstance.set((K)key);
      V result = myMap.remove(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return result;
    }
    //return myMap.remove(SoftKey.create(key));
  }

  public void clear() {
    processQueue();
    myMap.clear();
  }

  private static class Entry<K,V> implements Map.Entry<K,V> {
    private Map.Entry<?,V> ent;
    private K key;	/* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    Entry(Map.Entry<?, V> ent, K key) {
      this.ent = ent;
      this.key = key;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return ent.getValue();
    }

    public V setValue(V value) {
      return ent.setValue(value);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      return Comparing.equal(key,e.getKey()) && Comparing.equal(getValue(),e.getValue());
    }

    public int hashCode() {
      Object v;
      return (key == null ? 0 : key.hashCode()) ^ ((v = getValue()) == null ? 0 : v.hashCode());
    }
  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
    Set<Map.Entry<Key<K>,V>> hashEntrySet = myMap.entrySet();

    public Iterator<Map.Entry<K, V>> iterator() {
      return new Iterator<Map.Entry<K, V>>() {
        Iterator<Map.Entry<Key<K>, V>> hashIterator = hashEntrySet.iterator();
        Entry<K,V> next;

        public boolean hasNext() {
          while(hashIterator.hasNext()){
            Map.Entry<Key<K>, V> ent = hashIterator.next();
            Key<K> wk = ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null){
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new Entry<K, V>(ent, k);
            return true;
          }
          return false;
        }

        public Map.Entry<K,V> next() {
          if (next == null && !hasNext()) {
            throw new NoSuchElementException();
          }
          Entry<K,V> e = next;
          next = null;
          return e;
        }

        public void remove() {
          hashIterator.remove();
        }
      };
    }

    public boolean isEmpty() {
      return !iterator().hasNext();
    }

    public int size() {
      int j = 0;
      for(Iterator i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      Object ev = e.getValue();

      // optimization:
      Object key;
      myHardKeyInstance.set((K)o);
      key = myHardKeyInstance;
      //WeakKey key = WeakKey.create(e.getKey());

      Object hv = myMap.get(key);
      boolean toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
      if (toRemove){
        myMap.remove(key);
      }
      myHardKeyInstance.set(null);
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for(Iterator i = hashEntrySet.iterator(); i.hasNext();){
        Map.Entry ent = (Map.Entry)i.next();
        SoftKey wk = (SoftKey)ent.getKey();
        if (wk == null) continue;
        Object v = ent.getValue();
        h += wk.hashCode() ^ (v == null ? 0 : v.hashCode());
      }
      return h;
    }

  }

  public Set<Map.Entry<K, V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }
}
