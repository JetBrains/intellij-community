package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladimir Kondratyev
 */
public abstract class FinalizableCommand implements Runnable{
  private final Runnable myFinishCallBack;

  protected ToolWindowManager myManager;

  public FinalizableCommand(final Runnable finishCallBack){
    myFinishCallBack=finishCallBack;
  }

  public final void finish(){
    myFinishCallBack.run();
  }

  public void beforeExecute(final ToolWindowManager toolWindowManager) {
    myManager = toolWindowManager;
  }

  @Nullable
  public Condition getExpired() {
    return null;
  }

}
