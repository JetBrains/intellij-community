/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

public class Key<T> {
  private String myName; // for debug purposes only

  public Key(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

  public static <T> Key<T> create(String name) {
    return new Key<T>(name);
  }
}
