package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentHashSet<K> implements Set<K> {
  private ConcurrentMap<K, Boolean> map;

  public ConcurrentHashSet() {
    map = new ConcurrentHashMap<K, Boolean>();
  }
  public ConcurrentHashSet(TObjectHashingStrategy<K> hashingStrategy) {
    map = new ConcurrentHashMap<K, Boolean>(hashingStrategy);
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  public Iterator<K> iterator() {
    return map.keySet().iterator();
  }

  public Object[] toArray() {
    return map.keySet().toArray();
  }

  public <T> T[] toArray(T[] a) {
    return map.keySet().toArray(a);
  }

  public boolean add(K o) {
    return map.putIfAbsent(o, Boolean.TRUE) == null;
  }

  public boolean remove(Object o) {
    return map.keySet().remove(o);
  }

  public boolean containsAll(Collection<?> c) {
    return map.keySet().containsAll(c);
  }

  public boolean addAll(Collection<? extends K> c) {
    boolean ret = false;
    for (K o : c) {
      ret |= add(o);
    }

    return ret;
  }

  public boolean retainAll(Collection<?> c) {
    return map.keySet().retainAll(c);
  }

  public boolean removeAll(Collection<?> c) {
    return map.keySet().removeAll(c);
  }

  public void clear() {
    map.clear();
  }
}

