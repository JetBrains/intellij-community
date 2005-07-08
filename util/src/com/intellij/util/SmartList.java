/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings({"unchecked"})
public class SmartList<E> extends AbstractList<E> {
  private int mySize = 0;
  private Object myElem = null;

  public SmartList() {
  }

  public SmartList(Collection<? extends E> c) {
    addAll(c);
  }

  public E get(int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index > 0 && index < " + mySize);
    }
    if (mySize == 1) {
      return (E)myElem;
    }
    if (mySize == 2) {
      return (E)((Object[])myElem)[index];
    }
    else {
      return ((List<E>)myElem).get(index);
    }
  }

  public boolean add(E e) {
    if (mySize == 0) {
      myElem = e;
    }
    else if (mySize == 1) {
      Object[] array= new Object[2];
      array[0] = myElem;
      array[1] = e;
      myElem = array;
    }
    else if (mySize == 2) {
      List<E> list = new ArrayList<E>(3);
      final Object[] array = ((Object[])myElem);
      list.add((E)array[0]);
      list.add((E)array[1]);
      list.add(e);
      myElem = list;
    }
    else {
      ((List<E>)myElem).add(e);
    }

    mySize++;
    return true;
  }

  public int size() {
    return mySize;
  }

  public void clear() {
    myElem = null;
    mySize = 0;
  }

  public E set(final int index, final E element) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index > 0 && index < " + mySize);
    }
    final E oldValue;
    if (mySize == 1) {
      oldValue = (E)myElem;
      myElem = element;
    }
    else if (mySize == 2) {
      final Object[] array = ((Object[])myElem);
      oldValue = (E)array[index];
      array[index] = element;
    }
    else {
      oldValue = ((List<E>)myElem).set(index, element);
    }

    return oldValue;
  }
}

