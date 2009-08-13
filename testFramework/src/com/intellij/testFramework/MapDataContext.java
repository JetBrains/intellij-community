package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;

import java.util.HashMap;
import java.util.Map;

public class MapDataContext implements DataContext {
  private final Map myMap = new HashMap();

  public Object getData(String dataId) {
    return myMap.get(dataId);
  }

  public void put(String dataId, Object data) {
    myMap.put(dataId, data);
  }

  public <T> void put(DataKey<T> dataKey, T data) {
    put(dataKey.getName(), data);
  }
}
