package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPane {

  private Set<EventListener> myMouseListeners = new LinkedHashSet<EventListener>();
  private JRootPane myRootPane;

  private Component myCurrentOverComponent;
  private Component myMousePressedComponent;
  private Container myMousePressedContainer;

  public IdeGlassPaneImpl(JRootPane rootPane) {
    myRootPane = rootPane;
    setOpaque(false);
    setVisible(false);
    addMouseListener(new MouseAdapter() {
    });
    addMouseMotionListener(new MouseMotionAdapter() {
    });
    addMouseWheelListener(new MouseWheelListener() {
      public void mouseWheelMoved(final MouseWheelEvent e) {
      }
    });
  }

  protected void processMouseEvent(final MouseEvent e) {
    process(e, false);
  }

  protected void processMouseMotionEvent(final MouseEvent e) {
    process(e, true);
  }

  protected void processMouseWheelEvent(final MouseWheelEvent e) {
    process(e, false);
  }

  public void removeNotify() {
    // clear references to prevent memory leaks
    myCurrentOverComponent = null;
    myMousePressedComponent = null;
    myMousePressedContainer = null;
    super.removeNotify();
  }

  private void process(final MouseEvent e, boolean motionEvent) {
    final boolean processingDragEnd = myMousePressedComponent != null && e.getID() == MouseEvent.MOUSE_RELEASED;
    if (processingDragEnd) {
      final Point releasePoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myMousePressedComponent);
      redispatch(convertEvent(e, myMousePressedComponent, releasePoint, MouseEvent.MOUSE_RELEASED), motionEvent, myMousePressedComponent);

      myMousePressedComponent = null;
      myMousePressedContainer = null;
      return;
    }

    final boolean processingDrag = myMousePressedComponent != null && e.getID() == MouseEvent.MOUSE_DRAGGED;
    final JLayeredPane lp = myRootPane.getLayeredPane();
    final Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), lp);
    if (processForContainer(e, motionEvent, lp, lpPoint)) return;

    final Container cp = myRootPane.getContentPane();
    final Point cpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), cp);
    if (!processingDrag && cpPoint.y < 0) {
      final JMenuBar mb = myRootPane.getJMenuBar();
      if (mb != null) {
        if (processForContainer(e, motionEvent, mb, SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), mb))) return;
      }
    }

    processForContainer(e, motionEvent, cp, cpPoint);
  }

  private boolean processForContainer(final MouseEvent e, final boolean motionEvent, final Container container, final Point containerPoint) {
    final boolean dragEvent = e.getID() == MouseEvent.MOUSE_DRAGGED;
    Component target = SwingUtilities.getDeepestComponentAt(container, containerPoint.x, containerPoint.y);

    boolean processed = target != null;

    if (dragEvent && myMousePressedComponent != null) {
      target = myMousePressedComponent;
    }

    if (myCurrentOverComponent != target && !dragEvent) {
      if (myCurrentOverComponent != null) {
        redispatch(convertEventType(e, MouseEvent.MOUSE_EXITED), false, myCurrentOverComponent);
        if (target != null) {
          redispatch(convertEventType(e, MouseEvent.MOUSE_ENTERED), false, target);
        }
        processed = true;
      }
    }


    if (target != null) {
      redispatch(e, motionEvent, target);
      setCursor(target.getCursor());
      if (e.getID() == MouseEvent.MOUSE_PRESSED) {
        myMousePressedComponent = target;
        myMousePressedContainer = container;
      }
    }
    else {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      myMousePressedComponent = null;
      myMousePressedContainer = null;
    }

    myCurrentOverComponent = target;

    return processed;
  }

  private boolean redispatch(final MouseEvent originalEvent, final boolean isMotion, final Component target) {
    Container actualContainer = getParentOf(target);
    Component actualTarget = target;

    if (isEndComponent(actualTarget)) return false;

    MouseEvent targetEvent = convertEvent(originalEvent, actualTarget);
    for (EventListener eachListener : myMouseListeners) {
      if (isMotion && eachListener instanceof MouseMotionListener) {
        fireMouseMotion((MouseMotionListener)eachListener, targetEvent);
      }
      else if (!isMotion && eachListener instanceof MouseListener) {
        fireMouseEvent((MouseListener)eachListener, targetEvent);
      }
    }

    if (targetEvent.isConsumed()) {
      return true;
    }


    Set<Component> processed = new HashSet<Component>();
    while (true) {
      if (actualTarget == null || actualContainer == null) break;
      targetEvent = convertEvent(originalEvent, actualTarget);

      actualTarget.dispatchEvent(targetEvent);
      processed.add(actualTarget);

      if (targetEvent.isConsumed()) {
        return true;
      }

      boolean shouldProceed = isMotion ? actualTarget.getMouseMotionListeners().length == 0  : actualTarget.getMouseListeners().length == 0;
      if (shouldProceed) {
        Component sibling = null;
        Point containerPoint = SwingUtilities.convertPoint(originalEvent.getComponent(), originalEvent.getPoint(), actualContainer);
        for (int i = 0; i < actualContainer.getComponentCount(); i++) {
          final Component eachCandidate = actualContainer.getComponent(i);
          if (processed.contains(eachCandidate)) continue;
          if (eachCandidate.getBounds().contains(containerPoint)) {
            sibling = eachCandidate;
            break;
          }
        }
        if (sibling != null) {
          actualTarget = sibling;
        } else {
          actualTarget = getParentOf(actualTarget);
          actualContainer = getParentOf(actualTarget);
        }
      } else {
        break;
      }
      if (isEndComponent(actualTarget)) return false;
    }

    return false;
  }

  private MouseEvent convertEvent(final MouseEvent e, final Component target) {
    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
    return new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(), point.x, point.y, e.getClickCount(), e.isPopupTrigger(), e.getButton());
  }

  private static @Nullable Container getParentOf(final Component actualTarget) {
    return actualTarget != null ? actualTarget.getParent() : null;
  }

  private boolean isEndComponent(final Component actualTarget) {
    if (actualTarget == null) return true;
    if (actualTarget == myRootPane.getContentPane()) return true;
    if (actualTarget == myRootPane.getJMenuBar()) return true;
    if (actualTarget == myRootPane.getLayeredPane()) return true;
    return false;
  }

  private static MouseEvent convertEventType(MouseEvent template, int id) {
    return new MouseEvent(template.getComponent(), id, System.currentTimeMillis(), template.getModifiersEx(), template.getX(), template.getY(),
                          template.getClickCount(), template.isPopupTrigger(), template.getButton());
  }

  private static MouseEvent convertEvent(MouseEvent template, Component source, Point point, int id) {
    return new MouseEvent(source, id, System.currentTimeMillis(), template.getModifiersEx(), point.x, point.y,
                          template.getClickCount(), template.isPopupTrigger(), template.getButton());
  }


  private static void fireMouseEvent(final MouseListener listener, final MouseEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        listener.mousePressed(event);
        break;
      case MouseEvent.MOUSE_RELEASED:
        listener.mouseReleased(event);
        break;
      case MouseEvent.MOUSE_ENTERED:
        listener.mouseEntered(event);
        break;
      case MouseEvent.MOUSE_EXITED:
        listener.mouseExited(event);
        break;
      case MouseEvent.MOUSE_CLICKED:
        listener.mouseClicked(event);
        break;
    }
  }

  private static void fireMouseMotion(MouseMotionListener listener, final MouseEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_DRAGGED:
        listener.mouseDragged(event);
      case MouseEvent.MOUSE_MOVED:
        listener.mouseMoved(event);
    }
  }

  public void addMousePreprocessor(final MouseListener listener, Disposable parent) {
    _addListener(listener, parent);
  }


  public void addMouseMotionPreprocessor(final MouseMotionListener listener, final Disposable parent) {
    _addListener(listener, parent);
  }

  private void _addListener(final EventListener listener, final Disposable parent) {
    myMouseListeners.add(listener);
    if (!isVisible()) {
      setVisible(true);
    }
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myMouseListeners.remove(listener);
            if (myMouseListeners.size() == 0) {
              setVisible(false);
            }
          }
        });
      }
    });
  }

  public Component getTargetComponentFor(MouseEvent e) {
    Component candidate = findComponent(e, myRootPane.getLayeredPane());
    if (candidate != null) return candidate;
    candidate = findComponent(e, myRootPane.getContentPane());
    if (candidate != null) return candidate;
    return e.getComponent();
  }

  private Component findComponent(final MouseEvent e, final Container container) {
    final Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), container);
    final Component lpComponent = SwingUtilities.getDeepestComponentAt(container, lpPoint.x, lpPoint.y);
    return lpComponent;
  }


}
