/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.util.Collection;

/**
 * @author peter
 */
public class CollectConsumer<T> implements Consumer<T> {
  private final Collection<T> myResult;

  public CollectConsumer(Collection<T> result) {
    myResult = result;
  }

  public CollectConsumer() {
    this(new SmartList<T>());
  }

  public void consume(T t) {
    myResult.add(t);
  }

  public Collection<T> getResult() {
    return myResult;
  }
}
