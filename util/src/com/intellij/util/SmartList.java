/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class SmartList<E> extends AbstractList<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.SmartList");

  private int mySize;
  private E[] myArray1;
  private final E[] myArray2 = (E[])new Object[2];
  private List<E> myList;

  public SmartList() {
  }

  public SmartList(Collection<? extends E> c) {
    addAll(c);
  }

  public E get(int index) {
    if (index < 2) {
      return myArray2[index];
    }
    else {
      return myList.get(index);
    }
  }

  public boolean add(E e) {
    if (mySize < 2) {
      myArray2[mySize] = e;
    }
    else {
      if (mySize == 2) {
        if (myList == null) {
          myList = new ArrayList<E>(3);
        }
        myList.add(myArray2[0]);
        myList.add(myArray2[1]);
      }
      myList.add(e);
    }
    mySize++;
    return true;
  }

  public int size() {
    return mySize;
  }

  public void clear() {
    if (myList != null) {
      myList.clear();
    }
    if (myArray1 != null) {
      myArray1[0] = null;
    }
    if (myArray2 != null) {
      myArray2[0] = null;
      myArray2[1] = null;
    }
    mySize = 0;
  }

  public E[] toArray() {
    if (mySize > 2) {
      LOG.assertTrue(myList != null && myList.size() == mySize);
      return myList.toArray((E[])new Object[myList.size()]);
    }
    if (mySize == 2) {
      return myArray2;
    }
    if (mySize == 1) {
      if (myArray1 == null) {
        myArray1 = (E[])new Object[1];
      }
      myArray1[0] = myArray2[0];
      return myArray1;
    }
    return ArrayUtil.<E>emptyArray();
  }
}

