/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class LightEnumerationAdapter implements Enumeration{
  private LightEnumeration myEnum;
  private Object myCurrent;

  public LightEnumerationAdapter(LightEnumeration enumeration){
    myEnum = enumeration;
    myCurrent = null;
  }

  public boolean hasMoreElements(){
    return getNextElement() != null;
  }

  public Object nextElement(){
    Object result = getNextElement();
    myCurrent = null;
    if (result != null){
      return result;
    }
    else{
      throw new NoSuchElementException();
    }
  }

  private Object getNextElement(){
    if (myCurrent != null) return myCurrent;
    myCurrent = myEnum.nextElement();
    return myCurrent;
  }
}

