/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

public class ValidateableReference<T extends Validateable<T>> {
  private T myReferent;

  public ValidateableReference(T referent) {
    myReferent = referent;
  }

  public T get() {
    if (myReferent == null ||myReferent.isValid()) return myReferent;
    myReferent = myReferent.findMe();
    return myReferent;
  }
}