package com.intellij.debugger.impl;

import com.intellij.util.concurrency.Semaphore;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 27, 2004
 * Time: 12:56:52 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeAndWaitEventImpl implements InvokeAndWaitEvent{
  private Semaphore myInvokeAndWaitSemaphore;

  public final void release() {
    if(myInvokeAndWaitSemaphore != null) myInvokeAndWaitSemaphore.up();
  }

  public final void hold() {
    if(myInvokeAndWaitSemaphore == null) {
      myInvokeAndWaitSemaphore = new Semaphore();
    }
    myInvokeAndWaitSemaphore.down();
  }

  public void waitFor() {
    myInvokeAndWaitSemaphore.waitFor();
  }
}
