/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.enumeration;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class SequenceEnumeration implements Enumeration {
  private Enumeration myFirst;
  private Enumeration mySecond;
  private Enumeration myThird;
  private Enumeration myCurrent;
  private int myCurrentIndex;

  public SequenceEnumeration(Enumeration first, Enumeration second) {
    this(first, second, null);
  }

  public SequenceEnumeration(Enumeration first, Enumeration second, Enumeration third) {
    myFirst = first;
    mySecond = second;
    myThird = third;
    myCurrent = myFirst;
    myCurrentIndex = 0;
  }

  public boolean hasMoreElements() {
    if (myCurrentIndex == 3)
      return false;
    if (myCurrent != null && myCurrent.hasMoreElements()) {
      return true;
    }

    if (myCurrentIndex == 0) {
      myCurrent = mySecond;
    }
    else if (myCurrentIndex == 1) {
      myCurrent = myThird;
    }
    myCurrentIndex++;
    return hasMoreElements();
  }

  public Object nextElement() {
    if (!hasMoreElements()) {
      throw new NoSuchElementException();
    }
    return myCurrent.nextElement();
  }
}

