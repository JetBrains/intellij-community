package com.intellij.openapi.util;

import java.util.*;

public class MultiValuesMap<Key, Value>{
  private final Map<Key, Collection<Value>> myBaseMap = new HashMap<Key, Collection<Value>>();
  private static final ArrayList EMPTY_LIST = new ArrayList();

  public void put(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) {
      myBaseMap.put(key, new ArrayList<Value>());
    }

    myBaseMap.get(key).add(value);
  }

  public Collection<Value> get(Key key){
    return myBaseMap.get(key);
  }

  public Set<Key> keySet() {
    return myBaseMap.keySet();
  }

  public Collection<Value> values() {
    Set<Value> result = new HashSet<Value>();
    Iterator<Collection<Value>> lists = myBaseMap.values().iterator();
    while (lists.hasNext()) {
      result.addAll(lists.next());
    }

    return result;
  }

  public void remove(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) return;
    myBaseMap.get(key).remove(value);
  }
}
