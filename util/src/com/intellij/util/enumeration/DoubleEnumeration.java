/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class DoubleEnumeration implements Enumeration{
  private Object myValue1;
  private Object myValue2;
  private int myIndex;

  public DoubleEnumeration(Object value1, Object value2){
    myValue1 = value1;
    myValue2 = value2;
    myIndex = 0;
  }

  public Object nextElement(){
    switch(myIndex++){
      case 0: return myValue1;
      case 1: return myValue2;
      default: throw new NoSuchElementException();
    }
  }

  public boolean hasMoreElements(){
    return myIndex < 2;
  }
}
