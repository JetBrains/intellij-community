/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.*;

/**
 * @author peter
 */
public class SortedList<T> extends AbstractList<T>{
  private final Comparator<T> myComparator;
  private boolean mySorted;
  private final List<T> myDelegate = new ArrayList<T>();

  public SortedList(final Comparator<T> comparator) {
    myComparator = comparator;
  }

  @Override
  public void add(final int index, final T element) {
    mySorted = false;
    myDelegate.add(index, element);
  }

  @Override
  public T remove(final int index) {
    return myDelegate.remove(index);
  }

  public T get(final int index) {
    ensureSorted();
    return myDelegate.get(index);
  }

  private void ensureSorted() {
    if (!mySorted) {
      sort(myDelegate);
      mySorted = true;
    }
  }

  protected void sort(List<T> delegate) {
    Collections.sort(myDelegate, myComparator);
  }

  @Override
  public void clear() {
    myDelegate.clear();
  }

  public int size() {
    ensureSorted();
    return myDelegate.size();
  }
}
