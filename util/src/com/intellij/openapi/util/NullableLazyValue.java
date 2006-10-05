/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class NullableLazyValue<T> {
  private boolean myComputed;
  @Nullable private T myValue;

  @Nullable
  protected abstract T compute();

  @Nullable
  public final T getValue() {
    if (!myComputed) {
      myValue = compute();
      myComputed = true;
    }
    return myValue;
  }
}
