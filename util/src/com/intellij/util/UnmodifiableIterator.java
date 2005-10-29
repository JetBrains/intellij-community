/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import java.util.Iterator;

/**
 * @author max
 */
public class UnmodifiableIterator<T> implements Iterator<T> {
  private final Iterator<T> myOriginalIterator;

  public UnmodifiableIterator(final Iterator<T> originalIterator) {
    myOriginalIterator = originalIterator;
  }

  public boolean hasNext() {
    return myOriginalIterator.hasNext();
  }

  public T next() {
    return myOriginalIterator.next();
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
}
