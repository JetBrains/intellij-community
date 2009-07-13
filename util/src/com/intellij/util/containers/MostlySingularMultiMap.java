/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.util.Processor;
import gnu.trove.THashMap;

import java.util.Map;

public class MostlySingularMultiMap<K, V> {
  private final Map<K, Object> myMap = new THashMap<K, Object>();

  public void add(K key, V value) {
    Object current = myMap.get(key);
    if (current == null) {
      myMap.put(key, value);
    }
    else if (current instanceof Object[]) {
      Object[] curArr = (Object[])current;
      int size = curArr.length;
      Object[] newArr = new Object[size + 1];
      System.arraycopy(curArr, 0, newArr, 0, size);
      newArr[size] = value;

      myMap.put(key, newArr);
    }
    else {
      myMap.put(key, new Object[]{current, value});
    }
  }

  public boolean processForKey(K key, Processor<V> p) {
    return processValue(p, myMap.get(key));
  }

  private boolean processValue(Processor<V> p, Object v) {
    if (v instanceof Object[]) {
      for (Object o : (Object[])v) {
        if (!p.process((V)o)) return false;
      }
    }
    else if (v != null) {
      return p.process((V)v);
    }

    return true;
  }

  public boolean processAllValues(Processor<V> p) {
    for (Object v : myMap.values()) {
      if (!processValue(p, v)) return false;
    }

    return true;
  }
}
