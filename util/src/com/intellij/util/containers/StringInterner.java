/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.containers;

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class StringInterner extends THashSet<String> {
  public StringInterner() {
    super(5, 0.9f);
  }

  public StringInterner(final TObjectHashingStrategy<String> strategy) {
    super(5, 0.9f, strategy);
  }

  public String intern(String name) {
    int idx = index(name);
    if (idx >= 0) {
      return (String)_set[idx];
    }

    boolean added = add(name);
    assert added;

    return name;
  }
}
