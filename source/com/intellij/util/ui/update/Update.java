/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

public abstract class Update extends ComparableObject.Impl implements Runnable, Comparable {

  public static final int LOW_PRIORITY = 999;
  public static final int HIGH_PRIORITY = 10;

  private boolean myProcessed;
  private boolean myExecuteInWriteAction;

  private int myPriority = LOW_PRIORITY;

  public Update(@NonNls Object identity) {
    this(identity, false);
  }

  public Update(Object identity, int priority) {
    this(identity, false, priority);
  }

  public Update(Object identity, boolean executeInWriteAction) {
    this(identity, executeInWriteAction, LOW_PRIORITY);
  }

  public Update(Object identity, boolean executeInWriteAction, int priority) {
    super(identity);
    myExecuteInWriteAction = executeInWriteAction;
    myPriority = priority;
  }

  public boolean isDisposed() {
    return false;
  }
  
  public boolean isExpired() {
    return false;
  }

  public boolean wasProcessed() {
    return myProcessed;
  }

  public void setProcessed() {
    myProcessed = true;
  }

  public boolean executeInWriteAction() {
    return myExecuteInWriteAction;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return super.toString() + " Objects: " + Arrays.asList(getEqualityObjects());
  }

  public int compareTo(Object o) {
    Update another = (Update) o;

    int weightResult = getPriority() < another.getPriority() ? -1 : (getPriority() == another.getPriority() ? 0 : 1);

    if (weightResult == 0) {
      return  equals(o) ? 0 : 1;
    } 
    else {
      return weightResult;
    }
  }

  protected int getPriority() {
    return myPriority;
  }

  public boolean canEat(Update update) {
    return false;
  }
}
