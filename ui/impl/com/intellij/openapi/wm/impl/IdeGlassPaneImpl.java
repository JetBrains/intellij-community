package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ide.IdeEventQueue;

import javax.swing.*;
import javax.swing.event.MenuDragMouseEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.lang.ref.WeakReference;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPane, IdeEventQueue.EventDispatcher, Painter.Listener {

  private Set<EventListener> myMouseListeners = new LinkedHashSet<EventListener>();
  private JRootPane myRootPane;

  private WeakReference<Component> myCurrentOverComponent = new WeakReference<Component>(null);
  private WeakReference<Component> myMousePressedComponent = new WeakReference<Component>(null);

  private Set<Painter> myPainters = new LinkedHashSet<Painter>();
  private Map<Painter, Component> myPainter2Component = new LinkedHashMap<Painter, Component>();

  public IdeGlassPaneImpl(JRootPane rootPane) {
    myRootPane = rootPane;
    setOpaque(false);
    setVisible(false);
  }

  public boolean dispatch(final AWTEvent e) {
    boolean dispatched = false;

    if (e instanceof MouseEvent) {
      final MouseEvent me = (MouseEvent)e;
      Window eventWindow = me.getComponent() instanceof Window ? ((Window)me.getComponent()) : SwingUtilities.getWindowAncestor(me.getComponent());
      final Window thisGlassWindow = SwingUtilities.getWindowAncestor(myRootPane);
      if (eventWindow != thisGlassWindow) return false;
    }

    if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
      dispatched = preprocess((MouseEvent)e, false);
    } else if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
      dispatched = preprocess((MouseEvent)e, true);
    } else {
      return false;
    }

    if (isVisible()) {
      boolean cursorSet = false;
      MouseEvent me = (MouseEvent)e;
      if (me.getComponent() != null) {
        final Point point = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), myRootPane.getContentPane());

        if (myRootPane.getMenuBar() != null && myRootPane.getMenuBar().isVisible()) {
          point.y += myRootPane.getMenuBar().getHeight();
        }

        final Component target =
          SwingUtilities.getDeepestComponentAt(myRootPane.getContentPane().getParent(), point.x, point.y);
        if (target != null) {
          setCursor(target.getCursor());
          cursorSet = true;
        }
      }

      if (!cursorSet) {
        setCursor(Cursor.getDefaultCursor());
      }
    }

    return dispatched;
  }

  private boolean preprocess(final MouseEvent e, final boolean motion) {
    final MouseEvent event = convertEvent(e, myRootPane);
    for (EventListener each : myMouseListeners) {
      if (motion && each instanceof MouseMotionListener) {
        fireMouseMotion((MouseMotionListener)each, event);
      } else if (!motion && each instanceof MouseListener) {
        fireMouseEvent((MouseListener)each, event);
      }

      if (event.isConsumed()) {
        return true;
      }
    }

    return false;
  }

private MouseEvent convertEvent(final MouseEvent e, final Component target) {
    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
    if (e instanceof MouseWheelEvent) {
      final MouseWheelEvent mwe = (MouseWheelEvent)e;
      return new MouseWheelEvent(target, mwe.getID(), mwe.getWhen(), mwe.getModifiersEx(), point.x, point.y, mwe.getClickCount(),
                                 mwe.isPopupTrigger(), mwe.getScrollType(), mwe.getScrollAmount(), mwe.getWheelRotation());
    }
    else if (e instanceof MenuDragMouseEvent) {
      final MenuDragMouseEvent de = (MenuDragMouseEvent)e;
      return new MenuDragMouseEvent(target, de.getID(), de.getWhen(), de.getModifiersEx(), point.x, point.y, e.getClickCount(),
                                    e.isPopupTrigger(), de.getPath(), de.getMenuSelectionManager());

    }
    else {
      return new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(), point.x, point.y, e.getClickCount(), e.isPopupTrigger(),
                            e.getButton());
    }
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
    activateIfNeeded();
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            removeListener(listener);
          }
        });
      }
    });
  }

  public void removeMousePreprocessor(final MouseListener listener) {
    removeListener(listener);
  }

  public void removeMouseMotionPreprocessor(final MouseListener listener) {
    removeListener(listener);
  }

  private void removeListener(final EventListener listener) {
    myMouseListeners.remove(listener);
    deactivateIfNeeded();
  }

  private void deactivateIfNeeded() {
    if (!isVisible()) return;

    if (myPainters.size() == 0 && myMouseListeners.size() == 0) {
      IdeEventQueue.getInstance().removeDispatcher(this);
      setVisible(false);
    }
  }

  private void activateIfNeeded() {
    if (isVisible()) return;

    if (myPainters.size() > 0 || myMouseListeners.size() > 0) {
      IdeEventQueue.getInstance().addDispatcher(this, null);
      setVisible(true);
    }
  }

  public void addPainter(final Component component, final Painter painter, final Disposable parent) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? this : component);
    painter.addListener(this);
    activateIfNeeded();
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            removePainter(painter);
          }
        });
      }
    });
  }

  public void removePainter(final Painter painter) {
    myPainters.remove(painter);
    myPainter2Component.remove(painter);
    painter.removeListener(this);
    deactivateIfNeeded();
  }

  protected void paintComponent(final Graphics g) {
    if (myPainters.size() == 0) return;

    Graphics2D g2d = (Graphics2D)g;
    for (Painter painter : myPainters) {
      final Rectangle clip = g.getClipBounds();

      final Component component = myPainter2Component.get(painter);
      if (component.getParent() == null) continue;
      final Rectangle componentBounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), this);

      if (!painter.needsRepaint()) continue;

      if (clip.contains(componentBounds) || clip.intersects(componentBounds)) {
        final Point targetPoint = SwingUtilities.convertPoint(this, 0, 0, component);
        final Rectangle targetRect = new Rectangle(targetPoint, component.getSize());
        g2d.translate(-targetRect.x, -targetRect.y);
        painter.paint(component, g2d);
        g2d.translate(targetRect.x, targetRect.y);
      }
    }
  }

  public boolean hasPainters() {
    return myPainters.size() > 0;
  }

  public void onNeedsRepaint(final Painter painter, final JComponent dirtyComponent) {
    if (dirtyComponent != null && dirtyComponent.isShowing()) {
      final Rectangle rec = SwingUtilities.convertRectangle(dirtyComponent, dirtyComponent.getBounds(), this);
      if (rec != null) {
        repaint(rec);
        return;
      }
    }

    repaint();
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
