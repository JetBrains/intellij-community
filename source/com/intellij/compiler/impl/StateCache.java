package com.intellij.compiler.impl;

import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public abstract class StateCache<T> extends MapCache<T> {
  public StateCache(String storePath) {
    super(storePath);
  }

  public void update(String url, T state){
    if (!load()) {
      return;
    }
    if (state != null) {
      myMap.put(url, state);
      setDirty();
    }
    else {
      remove(url);
    }
  }

  public void remove(String url){
    if (!load()) {
      return;
    }
    myMap.remove(url);
    setDirty();
  }

  public T getState(String url){
    if (!load()) {
      return null;
    }
    return myMap.get(url);
  }

  public String[] getUrls() {
    if (!load()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final Set<String> keys = myMap.keySet();
    return keys.toArray(new String[keys.size()]);
  }

  public Iterator<String> getUrlsIterator() {
    if (!load()) {
      return Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY).iterator();
    }
    return myMap.keySet().iterator();
  }
  
}