/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.Vector;

public class EnumerationCopy implements Enumeration{
  private Vector myElements;
  private Enumeration myEnumeration;

  public EnumerationCopy(Enumeration enumeration){
    myElements = new Vector();
    while(enumeration.hasMoreElements()){
      myElements.add(enumeration.nextElement());
    }
    reset();
  }

  public void reset(){
    myEnumeration = myElements.elements();
  }

  public boolean hasMoreElements(){
    return myEnumeration.hasMoreElements();
  }

  public Object nextElement(){
    return myEnumeration.nextElement();
  }

  public int getElementCount(){
    return myElements.size();
  }
}
