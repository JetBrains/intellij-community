package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;

import java.beans.PropertyChangeListener;

public interface ToolWindowEx extends ToolWindow{
  /**
   */
  String PROP_AVAILABLE="available";
  /**
   */
  String PROP_ICON="icon";
  /**
   */
  String PROP_TITLE="title";

  /**
   * Removes specified property change listener.
   * @param l listener to be removed.
   */
  void removePropertyChangeListener(PropertyChangeListener l);

  /**
   * @return type of internal decoration of tool window.
   * @exception java.lang.IllegalStateException if tool window isn't installed.
   */
  ToolWindowType getInternalType();
}
