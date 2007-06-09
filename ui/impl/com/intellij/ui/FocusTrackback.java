package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
  private ComponentQuery myFocusedComponentQuery;
  private boolean myMustBeShown;

  public FocusTrackback(@NotNull Object requestor, Component parent, boolean mustBeShown) {
    this(requestor, SwingUtilities.getWindowAncestor(parent), mustBeShown);
  }

  public FocusTrackback(@NotNull Object requestor, Window parent, boolean mustBeShown) {
    myRequestorName = requestor.toString();
    myParentWindow = parent;
    myMustBeShown = mustBeShown;


    if (ApplicationManager.getApplication().isUnitTestMode() || wrongOS()) return;

    register(parent);

    final List<FocusTrackback> stack = getStackForRoot(myRoot);
    if (stack.indexOf(this) == 0) {
      final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      setFocusOwner(manager.getPermanentFocusOwner());
      if (getFocusOwner() == null) {
        final Window window = manager.getActiveWindow();
        if (window instanceof Provider) {
          final FocusTrackback other = ((Provider)window).getFocusTrackback();
          if (other != null) {
            setFocusOwner(other.getFocusOwner());
          }
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

  public void onShown(@NotNull final Component focusedComponent) {
    onShown(new ComponentQuery() {
      public Component getComponent() {
        return focusedComponent;
      }
    });
  }

  public void onShown(@NotNull ComponentQuery query) {
    myFocusedComponentQuery = query;
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
    final List<FocusTrackback> stack = getStackForRoot(myRoot);

    if (!stack.contains(this)) return;

    final int index = stack.indexOf(this);
    Component toFocus;
    if (index > 0) {
      final ComponentQuery query = stack.get(index - 1).myFocusedComponentQuery;
      toFocus = query != null ? query.getComponent() : null;
    } else {
      toFocus = getFocusOwner();
    }

    for (int i = index + 1; i < stack.size(); i++) {
      if (!stack.get(i).isDead()) {
        toFocus = null;
        break;
      }
    }


    if (toFocus != null) {
      final Component ownerBySwing = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (ownerBySwing != null) {
        final Window ownerBySwingWindow = SwingUtilities.getWindowAncestor(ownerBySwing);
        if (ownerBySwingWindow != null && ownerBySwingWindow == SwingUtilities.getWindowAncestor(toFocus)) {
          toFocus = ownerBySwing;
        }
      }
      toFocus.requestFocus();
    }

    stack.remove(this);
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
    if (myMustBeShown) {
      return myFocusedComponentQuery != null && myFocusedComponentQuery.getComponent() != null && !myFocusedComponentQuery.getComponent().isShowing();
    } else {
      return myParentWindow == null || !myParentWindow.isShowing();
    }
  }

  private void setFocusOwner(final Component focusOwner) {
    myFocusOwner = focusOwner;
  }

  public static interface Provider {
    FocusTrackback getFocusTrackback();
  }

  public static interface ComponentQuery {
    Component getComponent();
  }

}