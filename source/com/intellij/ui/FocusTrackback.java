package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.util.containers.WeakList;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;


public class FocusTrackback {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.FocusTrackback");

  private Window myRoot;
  private Component myFocusOwner;

  private static Map<Window,WeakList<FocusTrackback>> ourRootWindowToParentsStack = new WeakHashMap<Window, WeakList<FocusTrackback>>();

  private Object myRequestor;

  public FocusTrackback(Object requestor, Window parent) {
    myRequestor = requestor;

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    register(parent);

    final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    myFocusOwner = manager.getPermanentFocusOwner();
    if (myFocusOwner == null) {
      final Window window = manager.getActiveWindow();
      if (window instanceof Provider) {
        myFocusOwner = ((Provider)window).getFocusTrackback().getFocusOwner();
      }
    }
  }

  private void register(final Window parent) {
    myRoot = findUtlimateParent(parent);
    WeakList<FocusTrackback> stack = getStackForRoot(myRoot);
    stack.remove(this);
    stack.add(this);
  }

  public void restoreFocus() {
    if (!SystemInfo.isMac) return;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        _restoreFocus();
      }
    });
  }

  private void _restoreFocus() {
    if (myFocusOwner != null) {
      final WeakList<FocusTrackback> stack = getStackForRoot(myRoot);

      final boolean isLastInFocusChain = stack.contains(this) && stack.indexOf(this) == stack.size() - 1;
      if (isLastInFocusChain) {
        myFocusOwner.requestFocus();
      }

      dispose();
    }
  }

  private WeakList<FocusTrackback> getStackForRoot(final Window root) {
    WeakList<FocusTrackback> stack = ourRootWindowToParentsStack.get(root);
    if (stack == null) {
      stack = new WeakList<FocusTrackback>();
      ourRootWindowToParentsStack.put(root, stack);
    }
    return stack;
  }

  private Window findUtlimateParent(final Window parent) {
    Window root = parent == null ? JOptionPane.getRootFrame() : parent;
    while (root != null) {
      final Container next = root.getParent();
      if (next == null) break;
      if (next instanceof Window) {
        root = (Window)next;
      }
      final Window nextWindow = SwingUtilities.getWindowAncestor(next);
      if (nextWindow == null) break;
      root = nextWindow;
    }

    LOG.assertTrue(root instanceof IdeFrame);

    return root;
  }

  public Component getFocusOwner() {
    return myFocusOwner;
  }

  public String toString() {
    return getClass().getName() + " requestor: " + myRequestor;
  }

  public void dispose() {
    getStackForRoot(myRoot).remove(this);
  }


  public static interface Provider {
    FocusTrackback getFocusTrackback();
  }

}