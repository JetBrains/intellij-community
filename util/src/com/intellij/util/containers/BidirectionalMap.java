package com.intellij.util.containers;

import java.util.*;

public class BidirectionalMap<K,V> implements Map<K,V>{
  private Map<K,V> myKeyToValueMap = new com.intellij.util.containers.HashMap<K,V>();
  private Map<V,List<K>> myValueToKeysMap = new com.intellij.util.containers.HashMap<V,List<K>>();

  public V put(K key, V value){
    V oldValue = myKeyToValueMap.put(key, value);
    if (oldValue != null){
      if (oldValue.equals(value)) return oldValue;
      List<K> array = myValueToKeysMap.get(oldValue);
      array.remove(key);
    }

    List<K> array = myValueToKeysMap.get(value);
    if (array == null){
      array = new ArrayList<K>();
      myValueToKeysMap.put(value, array);
    }
    array.add(key);
    return oldValue;
  }

  public void clear() {
    myKeyToValueMap.clear();
    myValueToKeysMap.clear();
  }

  public List<K> getKeysByValue(V value){
    return myValueToKeysMap.get(value);
  }

  public boolean contaisValue(V value) {
    return myValueToKeysMap.containsKey(value);
  }

  public Set<K> keySet() {
    return myKeyToValueMap.keySet();
  }

  public int size(){
    return myKeyToValueMap.size();
  }

  public boolean isEmpty(){
    return myKeyToValueMap.isEmpty();
  }

  public boolean containsKey(Object key){
    return myKeyToValueMap.containsKey(key);
  }

  public boolean containsValue(Object value){
    return myValueToKeysMap.containsKey(value);
  }

  public V get(Object key) {
    return myKeyToValueMap.get(key);
  }

  public V remove(Object key){
    final V value = myKeyToValueMap.remove(key);
    final List<K> ks = myValueToKeysMap.get(value);
    if(ks.size() > 1) ks.remove(key);
    else myValueToKeysMap.remove(value);
    return value;
  }

  public void putAll(Map<? extends K, ? extends V> t){
    Set<? extends K> ks = t.keySet();
    final Iterator<? extends K> iterator = ks.iterator();
    while (iterator.hasNext()) {
      final K k = iterator.next();
      put(k, t.get(k));
    }
  }

  public Collection<V> values(){
    return myValueToKeysMap.keySet();
  }

  public Set<Map.Entry<K, V>> entrySet(){
    return myKeyToValueMap.entrySet();
  }
}
