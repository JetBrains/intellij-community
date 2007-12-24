/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.NotNullLazyValue;

import java.util.Iterator;

/**
 * @author peter
*/
public class LazyIterator<T> implements Iterator<T> {
  private NotNullLazyValue<Iterator<T>> myLazyValue;

  public LazyIterator(final NotNullLazyValue<Iterator<T>> lazyIterator) {
    myLazyValue = lazyIterator;
  }

  public boolean hasNext() {
    return myLazyValue.getValue().hasNext();
  }

  public T next() {
    return myLazyValue.getValue().next();
  }

  public void remove() {
    myLazyValue.getValue().remove();
  }
}
