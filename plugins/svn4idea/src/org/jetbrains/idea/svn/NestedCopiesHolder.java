// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class NestedCopiesHolder {

  private final Set<NestedCopyInfo> mySet = new HashSet<>();

  public synchronized void add(final @NotNull Set<NestedCopyInfo> data) {
    mySet.addAll(data);
  }

  public synchronized Set<NestedCopyInfo> getAndClear() {
    Set<NestedCopyInfo> copy = new HashSet<>(mySet);
    mySet.clear();

    return copy;
  }
}
