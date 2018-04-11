// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

public class InfoStorage<T> {
  public T myT;
  public InfoReliability myInfoReliability;

  public InfoStorage(final T t, final InfoReliability infoReliability) {
    myT = t;
    myInfoReliability = infoReliability;
  }

  public boolean accept(final InfoStorage<T> infoStorage) {
    boolean override = infoStorage.myInfoReliability.shouldOverride(myInfoReliability);

    if (override) {
      myT = infoStorage.myT;
      myInfoReliability = infoStorage.myInfoReliability;
    }

    return override;
  }

  public T getValue() {
    return myT;
  }

  public InfoReliability getInfoReliability() {
    return myInfoReliability;
  }
}
