/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CoModifiableList<T> extends AbstractList<T> {
  private ArrayList<T> myAfterIteratingElements = null;
  private final List<T> myElements;

  public CoModifiableList(List<T> elements) {
    myElements = elements;
  }

  public synchronized boolean add(T rangeMarker) {
    if (myAfterIteratingElements != null) myAfterIteratingElements.add(rangeMarker);
    else myElements.add(rangeMarker);
    return true;
  }

  public synchronized T remove(int index) {
    return myElements.remove(index);
  }

  public T get(int index) {
    return myElements.get(index);
  }

  public int size() {
    return myElements.size();
  }

  public void forEach(InnerIterator<T> innerIterator) {
    if (myAfterIteratingElements != null) {
      throw new RuntimeException("Nested iterations aren't supported");
    }
    try {
      myAfterIteratingElements = new ArrayList<T>();
      for (Iterator<T> iterator = myElements.iterator(); iterator.hasNext();) {
        T rangeMarker = iterator.next();
        if (rangeMarker == null) continue;
        innerIterator.process(rangeMarker, iterator);
      }
    } finally {
      synchronized(this) {
        for (Iterator<T> iterator = myAfterIteratingElements.iterator(); iterator.hasNext();) {
          T rangeMarker = iterator.next();
          myElements.add(rangeMarker);
        }
        myAfterIteratingElements = null;
      }
    }
  }

  public interface InnerIterator<T> {
    void process(T rangeMarker, Iterator<T> iterator);
  }
}
