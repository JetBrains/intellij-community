/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class EmptyIterator<T> implements Iterator<T> {
  public boolean hasNext() {
    return false;
  }

  public T next() {
    throw new NoSuchElementException();
  }

  public void remove() {
    throw new IllegalStateException();
  }
}
