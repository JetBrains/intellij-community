/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

/**
 * @author dyoma
 */
public class Enumerator<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.Enumerator");
  private final TObjectIntHashMap<T> myNumbers;
  private int myNextNumber = 1;

  public Enumerator(int expectNumber, TObjectHashingStrategy strategy) {
    myNumbers = new TObjectIntHashMap<T>(expectNumber, (TObjectHashingStrategy<T>) strategy);
  }

  public void clear() {
    myNumbers.clear();
    myNextNumber = 1;
  }

  public int[] enumerate(T[] objects) {
    int[] idx = new int[objects.length];
    for (int i = 0; i < objects.length; i++) {
      final T object = objects[i];
      final int number = enumerate(object);
      idx[i] = number;
    }
    return idx;
  }

  public int enumerate(T object) {
    final int res = enumerateImpl(object);
    return Math.max(res, -res);
  }

  public boolean add(T object) {
    final int res = enumerateImpl(object);
    return res < 0;
  }

  public int enumerateImpl(T object) {
    if( object == null ) return 0;

    int number = myNumbers.get(object);
    if (number == 0) {
      number = myNextNumber++;
      myNumbers.put(object, number);
      return -number;
    }
    return number;
  }

  public int get(T object) {
    if (object == null) return 0;
    final int res = myNumbers.get(object);

    if (res == 0)
      LOG.error( "Object must be already added to enumerator!" );

    return res;
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    for( TObjectIntIterator<T> iter = myNumbers.iterator(); iter.hasNext(); ) {
      iter.advance();
      buffer.append(Integer.toString(iter.value()) + ": " + iter.key().toString() + "\n");
    }
    return buffer.toString();
  }
}
