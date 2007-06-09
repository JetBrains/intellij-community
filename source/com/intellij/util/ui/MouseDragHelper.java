package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public abstract class MouseDragHelper implements MouseListener, MouseMotionListener {

  public static final int DRAG_START_DEADZONE = 7;

  private JComponent myDragComponent;

  private Point myPressPointScreen;
  private Point myPressPointComponent;

  private boolean myDraggingNow;
  private boolean myDragJustStarted;
  private IdeGlassPane myGlassPane;
  private Disposable myParentDisposable;
  private Dimension myDelta;

  public MouseDragHelper(Disposable parent, final JComponent dragComponent) {
    myDragComponent = dragComponent;
    myParentDisposable = parent;

  }

  public void start() {
    if (myGlassPane != null) return;

    new UiNotifyConnector(myDragComponent, new Activatable() {
      public void showNotify() {
        attach();
      }

      public void hideNotify() {
        detach();
      }
    });

    Disposer.register(myParentDisposable, new Disposable() {
      public void dispose() {
        stop();
      }
    });
  }

  private void attach() {
    myGlassPane = IdeGlassPaneUtil.find(myDragComponent);
    myGlassPane.addMousePreprocessor(this, myParentDisposable);
    myGlassPane.addMouseMotionPreprocessor(this, myParentDisposable);
  }

  public void stop() {
    detach();
  }

  private void detach() {
    myGlassPane.removeMousePreprocessor(this);
    myGlassPane.removeMouseMotionPreprocessor(this);
    myGlassPane = null;
  }

  public void mousePressed(final MouseEvent e) {
    if (!canStartDragging(e)) return;

    myPressPointScreen = new RelativePoint(e).getScreenPoint();
    myPressPointComponent = e.getPoint();

    myDelta = new Dimension();
    if (myDragComponent.isShowing()) {
      final Point delta = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myDragComponent);
      myDelta.width = delta.x;
      myDelta.height = delta.y;
    }
  }

  public void mouseReleased(final MouseEvent e) {
    boolean wasDragging = myDraggingNow;
    myPressPointScreen = null;
    myDraggingNow = false;
    myDragJustStarted = false;

    if (wasDragging) {
      try {
        processDragFinish(e);
      }
      finally {
        myDraggingNow = false;
        myPressPointComponent = null;
        myPressPointScreen = null;
        e.consume();
      }
    }
  }

  public void mouseDragged(final MouseEvent e) {
    if (myPressPointScreen == null) return;

    final boolean deadZone = isWithinDeadZone(e);
    if (!myDraggingNow && !deadZone) {
      myDraggingNow = true;
      myDragJustStarted = true;
    }
    else if (myDraggingNow) {
      myDragJustStarted = false;
    }

    if (myDraggingNow && myPressPointScreen != null) {
      final Point draggedTo = new RelativePoint(e).getScreenPoint();

      draggedTo.x -= myDelta.width;
      draggedTo.y -= myDelta.height;


      processDrag(e, draggedTo);

      e.consume();
    }
  }

  private boolean canStartDragging(MouseEvent me) {
    if (me.getButton() != MouseEvent.BUTTON1) return false;
    if (!myDragComponent.isShowing()) return false;

    Component component = me.getComponent();
    if (NullableComponent.Check.isNullOrHidden(component)) return false;
    final Point dragComponentPoint = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), myDragComponent);
    return canStartDragging(myDragComponent, dragComponentPoint);
  }

  protected boolean canStartDragging(final JComponent dragComponent, Point dragComponentPoint) {
    return true;
  }

  protected void processDragFinish(final MouseEvent even) {
  }

  public final boolean isDragJustStarted() {
    return myDragJustStarted;
  }

  protected abstract void processDrag(MouseEvent event, Point dragToScreenPoint);


  private boolean isWithinDeadZone(final MouseEvent e) {
    final Point screen = new RelativePoint(e).getScreenPoint();
    return Math.abs(myPressPointScreen.x - screen.x) < DRAG_START_DEADZONE &&
           Math.abs(myPressPointScreen.y - screen.y) < DRAG_START_DEADZONE;
  }

  public void mouseClicked(final MouseEvent e) {
  }

  public void mouseEntered(final MouseEvent e) {
  }

  public void mouseExited(final MouseEvent e) {
  }

  public void mouseMoved(final MouseEvent e) {
  }

}
