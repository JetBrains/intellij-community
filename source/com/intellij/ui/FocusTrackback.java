package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;


public class FocusTrackback {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.FocusTrackback");

  private Window myParentWindow;

  private Window myRoot;
  private Component myFocusOwner;

  private static Map<Window, ArrayList<FocusTrackback>> ourRootWindowToParentsStack = new WeakHashMap<Window, ArrayList<FocusTrackback>>();

  private Object myRequestor;

  public FocusTrackback(Object requestor, Window parent) {
    myRequestor = requestor;
    myParentWindow = parent;

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    register(parent);

    final ArrayList<FocusTrackback> stack = getStackForRoot(myRoot);
    if (stack.indexOf(this) == 0) {
      final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      myFocusOwner = manager.getPermanentFocusOwner();
      if (myFocusOwner == null) {
        final Window window = manager.getActiveWindow();
        if (window instanceof Provider) {
          myFocusOwner = ((Provider)window).getFocusTrackback().getFocusOwner();
        }
      }
    } else {
      myFocusOwner = stack.get(0).getFocusOwner();
    }
  }

  private void register(final Window parent) {
    myRoot = findUtlimateParent(parent);
    ArrayList<FocusTrackback> stack = getCleanStackForRoot();
    stack.remove(this);
    stack.add(this);
  }

  private ArrayList<FocusTrackback> getCleanStackForRoot() {
    ArrayList<FocusTrackback> stack = getStackForRoot(myRoot);

    final Object[] stackArray = stack.toArray();
    for (int i = 0; i < stackArray.length; i++) {
      FocusTrackback eachExisting = (FocusTrackback)stackArray[i];
      if (eachExisting != null && eachExisting.isDead()) {
        eachExisting.dispose();
      } else if (eachExisting == null) {
        stack.remove(eachExisting);
      }
    }
    return stack;
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
      final ArrayList<FocusTrackback> stack = getStackForRoot(myRoot);

      final boolean isLastInFocusChain = stack.contains(this) && stack.indexOf(this) == stack.size() - 1;
      if (isLastInFocusChain) {
        stack.clear();
        myFocusOwner.requestFocus();
      }

      dispose();
    }
  }

  private ArrayList<FocusTrackback> getStackForRoot(final Window root) {
    ArrayList<FocusTrackback> stack = ourRootWindowToParentsStack.get(root);
    if (stack == null) {
      stack = new ArrayList<FocusTrackback>();
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
    return getClass().getName() + " requestor: " + myRequestor + " parent=" + myParentWindow;
  }

  public void dispose() {
    getStackForRoot(myRoot).remove(this);
  }

  public final boolean isDead() {
    return myParentWindow != null ? !myParentWindow.isShowing() : true;
  }


  public static interface Provider {
    FocusTrackback getFocusTrackback();
  }

}