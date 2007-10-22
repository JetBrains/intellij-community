/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.dnd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * Utility tool to patch DnD listeners to enable multiple selection when original dnd is switched off
 *
 * @author spleaner
 */
public class DnDEnabler implements Activatable, Disposable {

  @NonNls public static final String KEY = "DragAndDropMultipleSelectionEnabler";

  private AWTEventListener myAwtListener = new MyAwtListener();
  private List<EventListener[]> myMouseListeners;
  //private List<MouseListener> myMousePreprocessors = new ArrayList<MouseListener>();
  private DnDAware myDnDSource;
  private MouseListener myOriginalDragGestureRecognizer;

  private PropertyChangeListener myPropertyChangeListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      if ("UI".equals(evt.getPropertyName())) {
        // todo[spleaner]: does default listeners are recreated onSetUI() and what 'bout custom listeners??
        onSetUI();
      }
    }
  };
  private MouseListener myTooltipListener1;
  private MouseListener myTooltipListener2;

  public DnDEnabler(@NotNull final DnDAware source, Disposable parent) {
    myDnDSource = source;
    final Component component = source.getComponent();

    component.addPropertyChangeListener(myPropertyChangeListener);

    final UiNotifyConnector connector = new UiNotifyConnector(component, this);// todo: disposable???
    Disposer.register(this, connector);
    Disposer.register(parent, this);

    onSetUI();
  }

  public void dispose() {
    myDnDSource.getComponent().removePropertyChangeListener(myPropertyChangeListener);
    myOriginalDragGestureRecognizer = null;
  }

  public void showNotify() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtListener);
    Toolkit.getDefaultToolkit().addAWTEventListener(myAwtListener, MouseEvent.MOUSE_EVENT_MASK);
  }

  public void hideNotify() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtListener);
  }

  public void onSetUI() {
    myMouseListeners = new ArrayList<EventListener[]>();
    new AwtVisitor(myDnDSource.getComponent()) {
      public boolean visit(Component component) {
        EventListener[] mouseListeners = component.getListeners(MouseListener.class);
        if (mouseListeners.length > 0) {
          myMouseListeners.add(mouseListeners);
          for (EventListener each : mouseListeners) {

            if (each instanceof MouseDragGestureRecognizer) {
              myOriginalDragGestureRecognizer = (MouseListener) each;
            }

            component.removeMouseListener((MouseListener)each);
          }
        }

        return false;
      }
    };

    readTooltipListeners();
  }

  private void readTooltipListeners() {
    final ToolTipManager manager = ToolTipManager.sharedInstance();

    myTooltipListener1 = manager;
    try {

//todo kirillk to detach mouseMotion listeners as well
      final Field moveBefore = manager.getClass().getDeclaredField("moveBeforeEnterListener");
      if (!MouseListener.class.isAssignableFrom(moveBefore.getType())) return;
      moveBefore.setAccessible(true);
      myTooltipListener2 = (MouseListener)moveBefore.get(manager);
    }
    catch (Exception e) {
      return;
    }
  }

  private static void dispatchMouseEvent(MouseListener listener, MouseEvent e) {
    if (listener != null) {
      int id = e.getID();
      switch (id) {
        case MouseEvent.MOUSE_PRESSED:
          listener.mousePressed(e);
          break;
        case MouseEvent.MOUSE_RELEASED:
          listener.mouseReleased(e);
          break;
        case MouseEvent.MOUSE_CLICKED:
          listener.mouseClicked(e);
          break;
        case MouseEvent.MOUSE_EXITED:
          listener.mouseExited(e);
          break;
        case MouseEvent.MOUSE_ENTERED:
          listener.mouseEntered(e);
          break;
      }
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isPressedToSelection(MouseEvent e) {
    if (MouseEvent.MOUSE_PRESSED != e.getID()) return false;
    if (!isPureButton1Event(e)) return false;
    return e.getClickCount() == 1 && myDnDSource.isOverSelection(e.getPoint());
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isPopupToSelection(MouseEvent e) {
    return e.isPopupTrigger() && myDnDSource.isOverSelection(e.getPoint());
  }

  private static boolean isPureButton1Event(MouseEvent event) {
    int button1 = MouseEvent.BUTTON1_MASK | MouseEvent.BUTTON1_DOWN_MASK;
    return (event.getModifiersEx() | button1) == button1;
  }

  private class MyAwtListener implements AWTEventListener {
    public void eventDispatched(AWTEvent event) {
      if (event instanceof MouseEvent) {
        MouseEvent e = (MouseEvent)event;

        Component comp = myDnDSource.getComponent();
        if (e.getComponent() != comp) return;

        //for (MouseListener each : myMousePreprocessors) {
        //  dispatchMouseEvent(each, e);
        //  if (e.isConsumed()) return;
        //}

        if (e.getComponent() == comp) {
          boolean shouldProcessTooltipManager = true;
          if (e.getComponent() instanceof JComponent) {
            final JComponent c = (JComponent)e.getComponent();
            if (c.getToolTipText() == null) {
              shouldProcessTooltipManager = false;
            }
          }

          if (isPressedToSelection(e)) {
            if (myDnDSource.getComponent().isFocusable()) {
              myDnDSource.getComponent().requestFocus();
            }
          }
          else {
            final boolean popupToSelection = isPopupToSelection(e);
            if (!e.isConsumed()) {
              assert e.getComponent() != null : "component is null! IDEADEV-6339";
              final EventListener[][] eventListeners = myMouseListeners.toArray(new EventListener[myMouseListeners.size()][]);
              for (EventListener[] listeners : eventListeners) {
                for (EventListener each : listeners) {
                  if (!shouldProcessTooltipManager) {
                    if (each == myTooltipListener1 || each == myTooltipListener2) continue;
                  }

                  if (popupToSelection) {
                    if (each != null && each.getClass().getName().indexOf("BasicTreeUI$DragFixHandler") >= 0) continue;
                  }

                  dispatchMouseEvent((MouseListener)each, e);
                  if (e.isConsumed()) break;
                }
              }

              if (shouldProcessTooltipManager) {
                ((JComponent)e.getComponent()).setToolTipText(null);
              }
            }
          }

          if (myOriginalDragGestureRecognizer != null) {
            dispatchMouseEvent(myOriginalDragGestureRecognizer, e);
          }
        }
      }
    }
  }

}
