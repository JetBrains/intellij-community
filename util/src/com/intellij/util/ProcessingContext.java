/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.util.Key;
import com.intellij.util.SharedProcessingContext;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public class ProcessingContext {
  private final Map<Object, Object> myMap = new THashMap<Object, Object>();
  private SharedProcessingContext mySharedContext;

  public ProcessingContext() {
  }

  public ProcessingContext(final SharedProcessingContext sharedContext) {
    mySharedContext = sharedContext;
  }

  @NotNull
  public SharedProcessingContext getSharedContext() {
    if (mySharedContext == null) {
      return mySharedContext = new SharedProcessingContext();
    }
    return mySharedContext;
  }

  public Object get(@NotNull @NonNls final String key) {
    return myMap.get(key);
  }

  public void put(@NotNull @NonNls final String key, @NotNull final Object value) {
    myMap.put(key, value);
  }

  public <T> void put(Key<T> key, T value) {
    myMap.put(key, value);
  }

  public <T> T get(Key<T> key) {
    return (T)myMap.get(key);
  }
}
