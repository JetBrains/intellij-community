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

/*
 * @author max
 */
package com.intellij.util.containers;

import gnu.trove.THashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class LockPoolSynchronizedMap<K, V> extends THashMap<K, V> {
  private static final int NUM_LOCKS = 256;
  private static final Object[] ourLocks = new Object[NUM_LOCKS];
  private static int ourLockAllocationCounter = 0;

  private final Object mutex = allocateLock();

  static {
    for (int i = 0; i < ourLocks.length; i++) {
      ourLocks[i] = new Object();
    }
  }

  public LockPoolSynchronizedMap() {
  }

  public LockPoolSynchronizedMap(final int initialCapacity) {
    super(initialCapacity);
  }

  public LockPoolSynchronizedMap(final int initialCapacity, final float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  private static Object allocateLock() {
    ourLockAllocationCounter = (ourLockAllocationCounter + 1) % NUM_LOCKS;
    return ourLocks[ourLockAllocationCounter];
  }

  public int size() {
    synchronized (mutex) {
      return super.size();
    }
  }

  public boolean isEmpty() {
    synchronized (mutex) {
      return super.isEmpty();
    }
  }

  public boolean containsKey(Object key) {
    synchronized (mutex) {
      return super.containsKey(key);
    }
  }

  public boolean containsValue(Object value) {
    synchronized (mutex) {
      return super.containsValue(value);
    }
  }

  public V get(Object key) {
    synchronized (mutex) {
      return super.get(key);
    }
  }

  public V put(K key, V value) {
    synchronized (mutex) {
      return super.put(key, value);
    }
  }

  public V remove(Object key) {
    synchronized (mutex) {
      return super.remove(key);
    }
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    synchronized (mutex) {
      super.putAll(map);
    }
  }

  public void clear() {
    synchronized (mutex) {
      super.clear();
    }
  }

  public LockPoolSynchronizedMap<K, V> clone() {
    synchronized (mutex) {
      return (LockPoolSynchronizedMap<K,V>)super.clone();
    }
  }

  public Set<K> keySet() {
    throw new UnsupportedOperationException();
  }

  public Set<Map.Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  public Collection<V> values() {
    throw new UnsupportedOperationException();
  }
}