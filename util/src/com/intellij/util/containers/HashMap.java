/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

public class HashMap<K,V>  extends java.util.HashMap<K,V> {
  public HashMap(int i, float v) {
    super(i, v);
  }

  public HashMap(int i) {
    super(i);
  }

  public HashMap() { }

  public <K1 extends K,V1 extends V> HashMap(java.util.Map<K1, V1> map) {
    super(map);
  }

  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
