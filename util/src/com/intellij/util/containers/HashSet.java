/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

public class HashSet<E> extends java.util.HashSet<E>{
  public HashSet() { }

  public <T extends E> HashSet(java.util.Collection<T> collection) {
    super(collection);
  }

  public HashSet(int i, float v) {
    super(i, v);
  }

  public HashSet(int i) {
    super(i);
  }

  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
