package com.intellij.util.ui;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;

public abstract class ChildFocusWatcher implements AWTEventListener, Disposable {
  private JComponent myParent;

  public ChildFocusWatcher(JComponent parent) {
    myParent = parent;
    Toolkit.getDefaultToolkit().addAWTEventListener(this, FocusEvent.FOCUS_EVENT_MASK);
  }

  public void eventDispatched(final AWTEvent event) {
    if (event instanceof FocusEvent) {
      final FocusEvent fe = (FocusEvent)event;
      final Component component = fe.getComponent();
      if (component == null) return;
      if (!SwingUtilities.isDescendingFrom(component, myParent)) return;

      if (fe.getID() == FocusEvent.FOCUS_GAINED) {
        onFocusGained(fe);
      } else if (fe.getID() == FocusEvent.FOCUS_LAST) {
        onFocusLost(fe);
      }
    }
  }

  public void dispose() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
  }

  protected abstract void onFocusGained(FocusEvent event);
  protected abstract void onFocusLost(FocusEvent event);
}
