package com.intellij.openapi.util;

import java.util.HashMap;
import java.util.Map;

public class MutualMap<Key, Value> {

  private Map<Key, Value> myKey2Value = new HashMap<Key, Value>();
  private Map<Value, Key> myValue2Key = new HashMap<Value, Key>();

  public void put(Key key, Value value) {
    myKey2Value.put(key, value);
    myValue2Key.put(value, key);
  }

  public Value getValue(Key key) {
    return myKey2Value.get(key);
  }

  public Key getKey(Value value) {
    return myValue2Key.get(value);
  }

  public int size() {
    return myValue2Key.size();
  }

  public boolean containsKey(final Key key) {
    return myKey2Value.containsKey(key);
  }

  public void remove(final Key key) {
    final Value value = myKey2Value.get(key);
    myKey2Value.remove(key);
    myValue2Key.remove(value);
  }
}
