// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class NestedCopiesHolder {

  private final Set<NestedCopyInfo> mySet = new HashSet<>();

  public synchronized void add(@NotNull final Set<NestedCopyInfo> data) {
    mySet.addAll(data);
  }

  public synchronized Set<NestedCopyInfo> getAndClear() {
    Set<NestedCopyInfo> copy = new HashSet<>(mySet);
    mySet.clear();

    return copy;
  }
}
