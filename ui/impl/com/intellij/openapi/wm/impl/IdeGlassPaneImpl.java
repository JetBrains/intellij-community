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
    final Container cp = myRootPane.getContentPane();
    final Point cpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), cp);
    if (cpPoint.y < 0) {
      final JMenuBar mb = myRootPane.getJMenuBar();
      processForContainer(e, motionEvent, mb, SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), mb));
    }
    else {
      processForContainer(e, motionEvent, cp, cpPoint);
    }
  }

  private void processForContainer(final MouseEvent e, final boolean motionEvent, final Container cp, final Point containerPoint) {
    final boolean dragEvent = e.getID() == MouseEvent.MOUSE_DRAGGED;
    Component target = SwingUtilities.getDeepestComponentAt(cp, containerPoint.x, containerPoint.y);
    if (dragEvent && myMousePressedComponent != null) {
      target = myMousePressedComponent;
    }

    if (myCurrentOverComponent != target && !dragEvent) {
      if (myCurrentOverComponent != null) {
        redispatch(convertEventType(e, MouseEvent.MOUSE_EXITED), false, myCurrentOverComponent);
        if (target != null) {
          redispatch(convertEventType(e, MouseEvent.MOUSE_ENTERED), false, target);
        }
      }
    }


    if (target != null && !redispatch(e, motionEvent, target)) {
      setCursor(target.getCursor());
      if (e.getID() == MouseEvent.MOUSE_PRESSED) {
        myMousePressedComponent = target;
      }
    }
    else {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      myMousePressedComponent = null;
    }

    myCurrentOverComponent = target;
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

      if (targetEvent.isConsumed()) return true;

      actualTarget.dispatchEvent(targetEvent);

      if (targetEvent.isConsumed()) return true;

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
    return false;
  }

  private static MouseEvent convertEventType(MouseEvent event, int id) {
    return new MouseEvent(event.getComponent(), id, System.currentTimeMillis(), event.getModifiersEx(), event.getX(), event.getY(),
                          event.getClickCount(), event.isPopupTrigger(), event.getButton());
  }

  private static void fireMouseEvent(final MouseListener listener, final MouseEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        listener.mousePressed(event);
      case MouseEvent.MOUSE_RELEASED:
        listener.mouseReleased(event);
      case MouseEvent.MOUSE_ENTERED:
        listener.mouseEntered(event);
      case MouseEvent.MOUSE_EXITED:
        listener.mouseExited(event);
      case MouseEvent.MOUSE_CLICKED:
        listener.mouseClicked(event);
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

}
