/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class SingleEnumeration implements Enumeration{
  private Object myValue;
  private boolean myAdvanced;

  public SingleEnumeration(Object value){
    myValue = value;
    myAdvanced = false;
  }

  public Object nextElement(){
    if (myAdvanced){
      throw new NoSuchElementException();
    }
    return myValue;
  }

  public boolean hasMoreElements(){
    return !myAdvanced;
  }
}
