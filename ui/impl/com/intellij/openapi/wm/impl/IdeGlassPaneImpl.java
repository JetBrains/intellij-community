package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.MenuDragMouseEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.lang.ref.WeakReference;

public class IdeGlassPaneImpl extends JPanel implements IdeGlassPane {

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

  public boolean isOpaque() {
    return false;
  }

  private void process(final MouseEvent e, boolean motionEvent) {
    final boolean processingDragEnd = getMousePressedComponent() != null && e.getID() == MouseEvent.MOUSE_RELEASED;
    boolean repaint = true;
    if (processingDragEnd) {
      final Point releasePoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), getMousePressedComponent());
      redispatch(convertEvent(e, getMousePressedComponent(), releasePoint, MouseEvent.MOUSE_RELEASED), motionEvent,
                 getMousePressedComponent());
    } else {
      final boolean processingDrag = getMousePressedComponent() != null && e.getID() == MouseEvent.MOUSE_DRAGGED;
      final JLayeredPane lp = myRootPane.getLayeredPane();
      final Point lpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), lp);
      if (!processForContainer(e, motionEvent, lp, lpPoint)) {
        boolean processed = false;
        final Container cp = myRootPane.getContentPane();
        final Point cpPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), cp);
        if (!processingDrag && cpPoint.y < 0) {
          final JMenuBar mb = myRootPane.getJMenuBar();
          if (mb != null) {
            processed = processForContainer(e, motionEvent, mb, SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), mb));
            if (processed) {
              repaint = false;
            }
          }
        }
        if (!processed) {
          processForContainer(e, motionEvent, cp, cpPoint);
        }
      }
    }

    if (repaint) {
      for (Painter eachPainter : myPainters) {
        if (eachPainter.needsRepaint()) {
          repaint();
          break;
        }
      }
    }
  }

  private boolean processForContainer(final MouseEvent e,
                                      final boolean motionEvent,
                                      final Container container,
                                      final Point containerPoint) {
    final boolean dragEvent = e.getID() == MouseEvent.MOUSE_DRAGGED;
    Component target = SwingUtilities.getDeepestComponentAt(container, containerPoint.x, containerPoint.y);

    boolean processed = target != null;

    if (dragEvent && getMousePressedComponent() != null) {
      target = getMousePressedComponent();
    }

    if (getCurrentOverComponent() != target && !dragEvent) {
      if (getCurrentOverComponent() != null) {
        redispatch(convertEventType(e, MouseEvent.MOUSE_EXITED), false, getCurrentOverComponent());
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
        setMousePressedComponent(target);
      }
    }
    else {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      setMousePressedComponent(null);
    }

    setCurrentOverComponent(target);

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

      boolean shouldProceed = isMotion ? actualTarget.getMouseMotionListeners().length == 0 : actualTarget.getMouseListeners().length == 0;
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
        }
        else {
          actualTarget = getParentOf(actualTarget);
          actualContainer = getParentOf(actualTarget);
        }
      }
      else {
        break;
      }
      if (isEndComponent(actualTarget)) return false;
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

  private static
  @Nullable
  Container getParentOf(final Component actualTarget) {
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
    return new MouseEvent(template.getComponent(), id, System.currentTimeMillis(), template.getModifiersEx(), template.getX(),
                          template.getY(), template.getClickCount(), template.isPopupTrigger(), template.getButton());
  }

  private static MouseEvent convertEvent(MouseEvent template, Component source, Point point, int id) {
    return new MouseEvent(source, id, System.currentTimeMillis(), template.getModifiersEx(), point.x, point.y, template.getClickCount(),
                          template.isPopupTrigger(), template.getButton());
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
    setVisibleIfNeeded();
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myMouseListeners.remove(listener);
            setInvisibleIfNeeded();
          }
        });
      }
    });
  }

  private void setInvisibleIfNeeded() {
    if (myMouseListeners.size() == 0 && myPainters.size() == 0) {
      setVisible(false);
    }
  }

  private void setVisibleIfNeeded() {
    if (!isVisible()) {
      setVisible(true);
    }
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

  public void addPainter(final Component component, final Painter painter, final Disposable parent) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? this : component);
    setVisibleIfNeeded();
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
    setInvisibleIfNeeded();
  }

  protected void paintComponent(final Graphics g) {
    if (myPainters.size() == 0) return;

    Graphics2D g2d = (Graphics2D)g;
    for (Painter painter : myPainters) {
      final Rectangle clip = g.getClipBounds();

      final Component component = myPainter2Component.get(painter);
      if (component.getParent() == null) continue;
      final Rectangle componentBounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), this);
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

  public static void main(String[] args) {
    final Disposable parent = new Disposable() {
      public void dispose() {
      }
    };

    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new FlowLayout());
    frame.getContentPane().add(content, BorderLayout.CENTER);

    final IdeGlassPaneImpl gp = new IdeGlassPaneImpl(frame.getRootPane());
    frame.getRootPane().setGlassPane(gp);

    final JButton button = new JButton("one");
    content.add(button);
    gp.addPainter(button, new Painter() {
      public void paint(final Component component, final Graphics2D g) {
        g.setColor(Color.blue);
        g.drawRect(0, 0, component.getWidth(), component.getHeight());
      }

      public boolean needsRepaint() {
        return false;
      }
    }, parent);

    content.add(new JScrollPane(new JTree()));


    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }


  @Nullable
  private Component getCurrentOverComponent() {
    return myCurrentOverComponent.get();
  }

  private void setCurrentOverComponent(final Component c) {
    if (getCurrentOverComponent() == c) return;
    myCurrentOverComponent = new WeakReference<Component>(c);
  }

  @Nullable
  private Component getMousePressedComponent() {
    return myMousePressedComponent.get();
  }

  private void setMousePressedComponent(final Component c) {
    if (getMousePressedComponent() == c) return;
    myMousePressedComponent = new WeakReference<Component>(c);
  }
}
