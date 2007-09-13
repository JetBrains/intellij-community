package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.WindowWatcher;

import javax.swing.*;
import java.awt.*;

/**
 * Requests focus for the specified tool window.
 *
 * @author Vladimir Kondratyev
 */
public final class RequestFocusInToolWindowCmd extends FinalizableCommand {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.commands.RequestFocusInToolWindowCmd");
  private final ToolWindowImpl myToolWindow;
  private final FocusWatcher myFocusWatcher;

  public RequestFocusInToolWindowCmd(final ToolWindowImpl toolWindow, final FocusWatcher focusWatcher, final Runnable finishCallBack) {
    super(finishCallBack);
    myToolWindow = toolWindow;
    myFocusWatcher = focusWatcher;
  }

  public final void run() {
    try {
      Component preferredFocusedComponent = null;

      if (myToolWindow.getContentManager().getSelectedContent() != null) {
        preferredFocusedComponent = myToolWindow.getContentManager().getSelectedContent().getPreferredFocusableComponent();
        if (preferredFocusedComponent != null) {
          preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)preferredFocusedComponent);
        }
      }

      if (preferredFocusedComponent == null) {
        preferredFocusedComponent = myFocusWatcher.getFocusedComponent();
      }

      if (preferredFocusedComponent == null) {
        preferredFocusedComponent = myFocusWatcher.getNearestFocusableComponent();
        if (preferredFocusedComponent instanceof JComponent) {
          preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)preferredFocusedComponent);
        }
      }

      if (preferredFocusedComponent != null) {
        // When we get remembered component this component can be already invisible
        if (!preferredFocusedComponent.isShowing()) {
          preferredFocusedComponent = null;
        }
      }

      if (preferredFocusedComponent == null) {
        final JComponent component = myToolWindow.getComponent();
        preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(component);
      }

      final Window owner = SwingUtilities.getWindowAncestor(myToolWindow.getComponent());
      if (owner == null) {
        return;
      }
      // if owner is active window or it has active child window which isn't floating decorator then
      // don't bring owner window to font. If we will make toFront every time then it's possible
      // the following situation:
      // 1. user prform refactoring
      // 2. "Do not show preview" dialog is popping up.
      // 3. At that time "preview" tool window is being activated and modal "don't show..." dialog
      // isn't active.
      if (owner.getFocusOwner() == null) {
        final Window activeWindow = getActiveWindow(owner.getOwnedWindows());
        if (activeWindow == null || (activeWindow instanceof FloatingDecorator)) {
          LOG.debug("owner.toFront()");
          //Thread.dumpStack();
          //System.out.println("------------------------------------------------------");
          owner.toFront();
        }
      }
      // Try to focus component which is preferred one for the tool window
      if (preferredFocusedComponent != null) {
        requestFocus(preferredFocusedComponent);
      }
      else {
        // If there is no preferred component then try to focus tool window itself
        final JComponent componentToFocus = myToolWindow.getComponent();
        requestFocus(componentToFocus);
      }
    }
    finally {
      finish();
    }
  }


  private void requestFocus(final Component c) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
    if (owner != null && owner == c) {
      myFocusWatcher.setFocusedComponentImpl(c);
      updateFocusedComponentForWatcher(c);
    }
    else {
      myManager.requestFocus(c, true).doWhenDone(new Runnable() {
        public void run() {
          updateFocusedComponentForWatcher(c);
        }
      });
    }
  }

  private void updateFocusedComponentForWatcher(final Component c) {
    final WindowWatcher watcher = ((WindowManagerImpl)WindowManager.getInstance()).getWindowWatcher();
    final FocusWatcher focusWatcher = watcher.getFocusWatcherFor(c);
    if (focusWatcher != null) {
      focusWatcher.setFocusedComponentImpl(c);
    }
  }

  /**
   * @return first active window from hierarchy with specified roots. Returns <code>null</code>
   *         if there is no active window in the hierarchy.
   */
  private Window getActiveWindow(final Window[] windows) {
    for (int i = 0; i < windows.length; i++) {
      Window window = windows[i];
      if (window.isShowing() && window.isActive()) {
        return window;
      }
      window = getActiveWindow(window.getOwnedWindows());
      if (window != null) {
        return window;
      }
    }
    return null;
  }
}
