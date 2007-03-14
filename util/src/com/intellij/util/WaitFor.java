/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import org.jetbrains.annotations.NonNls;
import junit.framework.Assert;


/**
 * @author kir
 */
public abstract class WaitFor {

  private static final int DEFAULT_STEP = 10;
  private static final int MAX_TIMEOUT = 600 * 1000;

  private long myWaitTime;
  private boolean myInterrupted;
  private boolean myConditionRealized;
  @NonNls public static final String WAIT_FOR_THREAD_NAME = "WaitFor thread";

  /** Blocking call */
  public WaitFor() {
    this(MAX_TIMEOUT);
  }

  public WaitFor(int timeoutMsecs) {
    this(timeoutMsecs, DEFAULT_STEP);
  }

  /** Blocking call */
  public WaitFor(int timeoutMsecs, final int step) {
    long started = System.currentTimeMillis();
    long deadline = timeoutMsecs == -1 ? Long.MAX_VALUE : started + timeoutMsecs;

    myConditionRealized = false;
    try {
      while(!(myConditionRealized = condition()) && (System.currentTimeMillis() < deadline)) {
          Thread.sleep(step);
      }
    } catch (InterruptedException e) {
      myInterrupted = true;
    }
    myWaitTime = System.currentTimeMillis() - started;
  }

  /** Non-blocking call */
  public WaitFor(final int timeoutMsecs, final Runnable toRunOnTrue) {
    new Thread(WAIT_FOR_THREAD_NAME) {
      public void run() {
        myConditionRealized = new WaitFor(timeoutMsecs) {
          protected boolean condition() {
            return WaitFor.this.condition();
          }
        }.isConditionRealized();

        if (myConditionRealized) {
          toRunOnTrue.run();
        }
      }
    }.start();
  }

  public long getWaitedTime() {
    return myWaitTime;
  }

  public boolean isConditionRealized() {
    return myConditionRealized;
  }

  public boolean isInterrupted() {
    return myInterrupted;
  }

  protected abstract boolean condition();

  public void assertCompleted() {
    assertCompleted("");
  }
  public void assertCompleted(String message) {
    Assert.assertTrue(message, condition());
  }
}
