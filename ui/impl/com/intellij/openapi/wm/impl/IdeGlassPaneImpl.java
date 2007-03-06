package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.EventListener;

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
        if (target.isFocusable()) {
          target.requestFocus();
        }
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
    Component actualTarget = target;

    while (true) {
      if (isEndComponent(actualTarget)) break;

      final MouseEvent targetEvent = SwingUtilities.convertMouseEvent(originalEvent.getComponent(), originalEvent, actualTarget);
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

      actualTarget.dispatchEvent(targetEvent);

      if (targetEvent.isConsumed()) {
        return true;
      }

      boolean shouldProceed = isMotion ? actualTarget.getMouseMotionListeners().length == 0  : actualTarget.getMouseListeners().length == 0;
      if (shouldProceed) {
        actualTarget = actualTarget.getParent();
      } else {
        break;
      }
    }

    return false;
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
