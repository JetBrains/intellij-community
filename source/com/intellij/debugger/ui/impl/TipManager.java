package com.intellij.debugger.ui.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.WeakMouseListener;
import com.intellij.debugger.ui.WeakMouseMotionListener;
import com.intellij.ui.ListenerUtil;
import com.intellij.util.Alarm;
import com.intellij.util.WeakListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jan 29, 2004
 * Time: 4:23:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class TipManager {
  private static int SHOW_DELAY = 1000;

  public static interface TipFactory {
    JComponent createToolTip (MouseEvent e);
  }

  private class MyMouseListener extends MouseAdapter {
    public void mouseEntered(MouseEvent e) {
      tryTooltip(e);
    }

    private boolean isOverTip(MouseEvent e) {
      if (myCurrentTooltip != null) {
        Window tipWindow = SwingUtilities.windowForComponent(myCurrentTooltip);
        if(!tipWindow.isShowing()) hideTooltip();
        Point point = e.getComponent().getLocationOnScreen();
        point.translate(e.getX(), e.getY());
        return tipWindow != null && tipWindow.getBounds().contains(point);
      }
      return false;
    }

    public void mouseExited(MouseEvent e) {
      myAlarm.cancelAllRequests();
      if (isOverTip(e)) {
        ListenerUtil.addMouseListener(myCurrentTooltip, new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
              if (myCurrentTooltip != null) {
                if(!isOverTip(e)) {
                  SwingUtilities.windowForComponent(myCurrentTooltip).removeMouseListener(this);
                  hideTooltip();
                }
              }
            }
          });
      } else {
        hideTooltip();
      }
    }
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    public void mouseMoved(MouseEvent e) {
      tryTooltip(e);
    }
  }

  private void tryTooltip(final MouseEvent e) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        showTooltip(e);
      }
    }, DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY);
  }

  private void showTooltip(MouseEvent e) {
    JComponent newTip = myTipFactory.createToolTip(e);
    if(newTip == myCurrentTooltip) return;

    hideTooltip();

    if(newTip != null && myComponent.isShowing()) {
      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      Point sLocation = myComponent.getLocationOnScreen();
      Point location = newTip.getLocation();
      location.x += sLocation.x;
      location.y += sLocation.y;

      Popup tipPopup = popupFactory.getPopup(myComponent, newTip,
                                        location.x,
                                        location.y);
      tipPopup.show();
      myCurrentTooltip = newTip;
    }
  }

  public void hideTooltip() {
    if (myCurrentTooltip != null) {
      Window window = SwingUtilities.windowForComponent(myCurrentTooltip);
      if(window != null) window.hide();
      myCurrentTooltip = null;
    }
  }

  private JComponent myCurrentTooltip;
  private final TipFactory myTipFactory;
  private final JComponent myComponent;
  private MouseListener myMouseListener = new MyMouseListener();
  private MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();
  private final Alarm myAlarm = new Alarm();


  public TipManager(JComponent component, TipFactory factory) {
    new WeakMouseListener(component, myMouseListener);
    new WeakMouseMotionListener(component, myMouseMotionListener);

    myTipFactory = factory;
    myComponent = component;
  }

  public void dispose() {
    myMouseListener = null;
    myMouseMotionListener = null;
  }

}
