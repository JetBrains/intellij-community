package com.intellij.openapi.wm.impl.commands;

/**
 * @author Vladimir Kondratyev
 */
public abstract class FinalizableCommand implements Runnable{
  private final Runnable myFinishCallBack;

  public FinalizableCommand(final Runnable finishCallBack){
    myFinishCallBack=finishCallBack;
  }

  public final void finish(){
    myFinishCallBack.run();
  }
}
