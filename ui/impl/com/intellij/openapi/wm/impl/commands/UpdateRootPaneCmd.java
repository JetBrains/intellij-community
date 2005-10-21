package com.intellij.openapi.wm.impl.commands;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public final class UpdateRootPaneCmd extends FinalizableCommand{
  private final JRootPane myRootPane;

  public UpdateRootPaneCmd(final JRootPane rootPane, final Runnable finishCallBack){
    super(finishCallBack);
    if(rootPane==null){
      throw new IllegalArgumentException("rootPane cannot be null");
    }
    myRootPane=rootPane;
  }

  public void run(){
    try{
      myRootPane.revalidate();
      myRootPane.repaint();
    }finally{
      finish();
    }
  }
}

