package com.intellij.debugger.impl;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 7, 2004
 * Time: 2:40:47 PM
 * To change this template use File | Settings | File Templates.
 */
public interface InvokeAndWaitEvent {
  public void release();
  public void hold   ();

  public void waitFor();
}
