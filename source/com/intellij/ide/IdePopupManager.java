package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.IdePopupManager");

  private IdePopup myActivePopup;

  boolean isPopupActive() {
    if (myActivePopup != null) {
      if (!myActivePopup.getComponent().isShowing()) {
        myActivePopup = null;
        LOG.error("Popup is set up as active but not showing");
      }
    }
    return myActivePopup != null;
  }

  public boolean dispatch(AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if ((e instanceof KeyEvent) || (e instanceof MouseEvent) || (e instanceof MouseWheelEvent)) {
      return myActivePopup.dispatch(e);
    }

    return false;
  }

  public void setActivePopup(IdePopup popup) {
    myActivePopup = popup;
  }

  public void resetActivePopup() {
    myActivePopup = null;
  }
}
