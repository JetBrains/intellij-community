/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.AbstractSet;
import java.util.ArrayList;

public class ArrayListSet<E> extends AbstractSet<E> {
  private final ArrayList<E> myList = new ArrayList<E>();

  public java.util.Iterator<E> iterator() {
    return myList.iterator();
  }

  public int size() {
    return myList.size();
  }

  public boolean contains(Object object) {
    return myList.contains(object);
  }

  public boolean add(E e) {
    if (!myList.contains(e)){
      myList.add(e);
      return true;
    }
    else{
      return false;
    }
  }

  public boolean remove(Object object) {
    return myList.remove(object);
  }

  public void clear() {
    myList.clear();
  }
}
