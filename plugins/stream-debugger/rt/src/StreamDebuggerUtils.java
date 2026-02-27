// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.streams.rt;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
@SuppressWarnings("unused")
public final class StreamDebuggerUtils {
  private StreamDebuggerUtils() { }

  public static <V> Object[] formatObjectMap(Map<Integer, V> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final Object[] values = new Object[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static Object[] formatIntMap(Map<Integer, Integer> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final int[] values = new int[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static Object[] formatLongMap(Map<Integer, Long> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final long[] values = new long[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static Object[] formatBooleanMap(Map<Integer, Boolean> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final boolean[] values = new boolean[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static Object[] formatDoubleMap(Map<Integer, Double> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final double[] values = new double[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static Object[] computeDistinctMapping(Map<Integer, Object> beforeMap, Map<Integer, Object> afterMap) {
    Map<Object, Map<Integer, Object>> eqClasses = new HashMap<>();
    for (int beforeTime : beforeMap.keySet()) {
      Object beforeValue = beforeMap.get(beforeTime);
      eqClasses.computeIfAbsent(beforeValue, k -> new HashMap<>()).put(beforeTime, beforeValue);
    }
    Map<Integer, Integer> mapping = new LinkedHashMap<>();
    for (int afterTime : afterMap.keySet()) {
      Object afterValue = afterMap.get(afterTime);
      Map<Integer, Object> classes = eqClasses.get(afterValue);
      if (classes != null) {
        for (int classElementTime : classes.keySet()) {
          mapping.put(classElementTime, afterTime);
        }
      }
    }
    int size = mapping.size();
    int[] keys = new int[size];
    int[] values = new int[size];
    int i = 0;
    for (int key : mapping.keySet()) {
      keys[i] = key;
      values[i] = mapping.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }

  public static int test() {
    return 0;
  }
}
