package com.intellij.uiDesigner;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class EventProcessor {
  private Cursor myCursor;

  public final Cursor getCursor(){
    return myCursor;
  }

  public final void setCursor(final Cursor cursor){
    myCursor = cursor;
  }

  protected abstract void processKeyEvent(KeyEvent e);

  protected abstract void processMouseEvent(MouseEvent e);
  
  /**
   * @return true if processor cancelled its operation; false otherwise
   */ 
  protected abstract boolean cancelOperation();
}
