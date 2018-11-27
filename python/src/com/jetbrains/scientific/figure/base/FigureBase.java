// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.figure.base;

import com.jetbrains.scientific.figure.Figure;

import static com.google.common.base.Preconditions.checkState;

public abstract class FigureBase implements Figure {
  private Object mySearchKey;

  @Override
  public boolean hasSearchKey() {
    return mySearchKey != null;
  }

  @Override
  public Object getSearchKey() {
    checkState(hasSearchKey(), "Search key is not defined");
    return mySearchKey;
  }

  public void setSearchKey(Object searchKey) {
    mySearchKey = searchKey;
  }
}
