/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class ArrayListEnumeration implements Enumeration {
  private final ArrayList myArrayList;
  private int myCounter;

  public ArrayListEnumeration(ArrayList arraylist) {
    myArrayList = arraylist;
    myCounter = 0;
  }

  public Object nextElement() {
    if (myCounter >= myArrayList.size()) {
      throw new NoSuchElementException();
    } else {
      return myArrayList.get(myCounter++);
    }
  }

  public boolean hasMoreElements() {
    return myCounter < myArrayList.size();
  }
}
