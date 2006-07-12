/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.IdePopup;
import com.intellij.openapi.ui.popup.JBPopup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Stack;

public class StackingPopupDispatcher implements AWTEventListener, KeyEventDispatcher, IdePopup {

  private Stack<JBPopupImpl> myStack = new Stack<JBPopupImpl>();

  private static StackingPopupDispatcher ourInstance = new StackingPopupDispatcher();

  private StackingPopupDispatcher() {
  }

  public static StackingPopupDispatcher getInstance() {
    return StackingPopupDispatcher.ourInstance;
  }

  public static void onPopupShown(JBPopup popup) {
    ourInstance.myStack.push((JBPopupImpl)popup);
    if (ApplicationManager.getApplication() != null) {
      IdeEventQueue.getInstance().getPopupManager().setActivePopup(ourInstance);
    }
  }

  public static void onPopupHidden(JBPopup popup) {
    ourInstance.myStack.remove(popup);

    if (ourInstance.myStack.isEmpty()) {
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().resetActivePopup();
      }
    }
  }

  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  private static boolean dispatchMouseEvent(AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (ourInstance.myStack.isEmpty()) {
      return false;
    }

    JBPopupImpl popup = ourInstance.myStack.peek();
    final MouseEvent mouseEvent = ((MouseEvent) event);

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    while (true) {
      final Component content = popup.getContent();
      if (!content.isShowing()) {
        popup.cancel();
        return false;
      }

      final Rectangle bounds = new Rectangle(content.getLocationOnScreen(), content.getSize());
      if (bounds.contains(point)) {
        return false;
      }

      if (!popup.canClose()){
        return false;
      }
      popup.cancel();
      if (ourInstance.myStack.isEmpty()) {
        return false;
      }
      popup = ourInstance.myStack.peek();
    }
  }

  public boolean dispatchKeyEvent(final KeyEvent e) {
    /*
    if (ourInstance.myStack.isEmpty()) {
      return false;
    }

    JBPopupImpl popup = ourInstance.myStack.peek();
    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
      popup.cancel();
      return true;
    }
    */

    return false;
  }


  @Nullable
  public Component getComponent() {
    return myStack.peek().getContent();
  }

  public boolean dispatch(AWTEvent event) {
   if (event instanceof KeyEvent) {
      return dispatchKeyEvent(((KeyEvent) event));
   } else if (event instanceof MouseEvent) {
     return dispatchMouseEvent(event);
   } else {
     return false;
   }
  }

  public boolean closeActivePopup() {
    final JBPopupImpl popup = myStack.pop();
    if (popup != null && popup.isVisible()){
      popup.cancel();
      return true;
    }
    return false;
  }
}
