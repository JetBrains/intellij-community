/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

/**
 * @author ven
 */
public class Ref<T> {
  private T myValue;

  public Ref() { }

  public Ref(T value) {
    myValue = value;
  }

  public boolean isNull () {
    return myValue == null;
  }

  public T get () {
    return myValue;
  }

  public void set (T value) {
    myValue = value;
  }

  public static <T> Ref<T> create(T value) {
    return new Ref<T>(value);
  }
}
