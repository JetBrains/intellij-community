/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

/**
 * @author dsl
 */
public interface Condition<T> {
  boolean value(T object);

  class Not<T> implements Condition<T> {
    private final Condition<T> myCondition;

    private Not(Condition<T> condition) {
      myCondition = condition;
    }

    public boolean value(T value) {
      return !myCondition.value(value);
    }

    public static <T> Condition<T> create(Condition<T> condition) {
      if (condition instanceof Not) return ((Not<T>)condition).myCondition;
      else return new Not<T>(condition);
    }
  }
}
