package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


public class FocusTrackback {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.FocusTrackback");

  private Window myParentWindow;

  private Window myRoot;
  private Component myFocusOwner;

  private static final Map<Window, List<FocusTrackback>> ourRootWindowToParentsStack = new WeakHashMap<Window, List<FocusTrackback>>();

  private String myRequestorName;

  public FocusTrackback(@NotNull Object requestor, Component parent) {
    this(requestor, SwingUtilities.getWindowAncestor(parent));  
  }

  public FocusTrackback(@NotNull Object requestor, Window parent) {
    myRequestorName = requestor.toString();
    myParentWindow = parent;


    if (ApplicationManager.getApplication().isUnitTestMode() || wrongOS()) return;

    register(parent);

    final List<FocusTrackback> stack = getStackForRoot(myRoot);
    if (stack.indexOf(this) == 0) {
      final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      setFocusOwner(manager.getPermanentFocusOwner());
      if (getFocusOwner() == null) {
        final Window window = manager.getActiveWindow();
        if (window instanceof Provider) {
          setFocusOwner(((Provider)window).getFocusTrackback().getFocusOwner());
        }
      }
    } else {
      setFocusOwner(stack.get(0).getFocusOwner());
    }
  }

  private static boolean wrongOS() {
    //return !SystemInfo.isMac;
    return false;
  }

  private void register(final Window parent) {
    myRoot = findUtlimateParent(parent);
    List<FocusTrackback> stack = getCleanStackForRoot();
    stack.remove(this);
    stack.add(this);
  }

  private List<FocusTrackback> getCleanStackForRoot() {
    List<FocusTrackback> stack = getStackForRoot(myRoot);

    final FocusTrackback[] stackArray = stack.toArray(new FocusTrackback[stack.size()]);
    for (FocusTrackback eachExisting : stackArray) {
      if (eachExisting != null && eachExisting.isDead()) {
        eachExisting.dispose();
      }
      else if (eachExisting == null) {
        stack.remove(eachExisting);
      }
    }
    return stack;
  }

  public void restoreFocus() {
    if (wrongOS()) return;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        _restoreFocus();
      }
    });
  }

  private void _restoreFocus() {
    final Component focusOwner = getFocusOwner();
    if (focusOwner != null) {
      final List<FocusTrackback> stack = getStackForRoot(myRoot);

      final boolean isFirstInStack = stack.contains(this) && stack.indexOf(this) == 0;
      if (isFirstInStack && stack.size() == 1) {
        focusOwner.requestFocus();
      }
      stack.remove(this);
    }
    dispose();
  }

  private static List<FocusTrackback> getStackForRoot(final Window root) {
    List<FocusTrackback> stack = ourRootWindowToParentsStack.get(root);
    if (stack == null) {
      stack = new ArrayList<FocusTrackback>();
      ourRootWindowToParentsStack.put(root, stack);
    }
    return stack;
  }

  @Nullable
  private static Window findUtlimateParent(final Window parent) {
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

    return root;
  }

  @Nullable
  public Component getFocusOwner() {
    return myFocusOwner;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return getClass().getName() + " requestor: " + myRequestorName + " parent=" + myParentWindow;
  }

  public void dispose() {
    getStackForRoot(myRoot).remove(this);
    myParentWindow = null;
    myRoot = null;
    myFocusOwner = null;
  }

  private boolean isDead() {
    return myParentWindow == null || !myParentWindow.isShowing();
  }

  private void setFocusOwner(final Component focusOwner) {
    myFocusOwner = focusOwner;
  }

  public static interface Provider {
    FocusTrackback getFocusTrackback();
  }

}