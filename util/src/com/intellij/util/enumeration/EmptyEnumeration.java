/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public final class EmptyEnumeration implements Enumeration {
  public static Enumeration INSTANCE = new EmptyEnumeration();

  private EmptyEnumeration(){
  }

  public boolean hasMoreElements(){
    return false;
  }

  public Object nextElement(){
    throw new NoSuchElementException();
  }
}


