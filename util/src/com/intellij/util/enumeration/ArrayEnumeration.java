/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class ArrayEnumeration implements Enumeration {
  private Object[] myArray;
  private int myCurrent;

  public ArrayEnumeration(Object[] array) {
    myArray = array;
    myCurrent = 0;
  }

  public boolean hasMoreElements() {
    return myCurrent < myArray.length;
  }

  public Object nextElement() {
    if (myCurrent < myArray.length){
      return myArray[myCurrent++];
    }
    else{
      throw new NoSuchElementException();
    }
  }
}

