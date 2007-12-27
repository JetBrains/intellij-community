package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.util.EventDispatcher;

import javax.swing.*;
import java.util.EventListener;

/**
 * @author nik
 */
public abstract class AbstractBreakpointPanel<B> {
  private final String myTabName;
  private final String myHelpID;
  private final Class<B> myBreakpointClass;
  private final EventDispatcher<ChangesListener> myEventDispatcher = EventDispatcher.create(ChangesListener.class);

  protected AbstractBreakpointPanel(final String tabName, final String helpID, final Class<B> breakpointClass) {
    myTabName = tabName;
    myHelpID = helpID;
    myBreakpointClass = breakpointClass;
  }

  public String getTabTitle() {
    return myTabName;
  }

  public String getHelpID() {
    return myHelpID;
  }

  public abstract void dispose();

  public abstract Icon getTabIcon();

  public abstract void resetBreakpoints();

  public abstract void saveBreakpoints();

  public abstract JPanel getPanel();

  public abstract boolean canSelectBreakpoint(B breakpoint);

  public abstract void selectBreakpoint(B breakpoint);

  public void addChangesListener(ChangesListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangesListener(ChangesListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public Class<B> getBreakpointClass() {
    return myBreakpointClass;
  }

  public void ensureSelectionExists() {
  }

  protected void fireBreakpointsChanged() {
    myEventDispatcher.getMulticaster().breakpointsChanged();
  }

  public interface ChangesListener extends EventListener {
    void breakpointsChanged();
  }
}
