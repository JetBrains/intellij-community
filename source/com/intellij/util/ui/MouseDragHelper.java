package com.intellij.util.ui;

import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

public abstract class MouseDragHelper {

  public static final int DRAG_START_DEADZONE = 7;
  private Point myPressPoint;

  private boolean myDragging;
  private boolean myDragJustStarted;

  public MouseDragHelper(final JComponent dragComponent) {
    dragComponent.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        myPressPoint = e.getPoint();
      }
      public void mouseReleased(final MouseEvent e) {
        boolean wasDragging = myDragging;
        myPressPoint = null;
        myDragging = false;
        myDragJustStarted = false;

        if (wasDragging) {
          processDragFinish(e);
        }
      }
      public void mouseClicked(final MouseEvent e) {
        MouseDragHelper.this.mouseClicked(e);
      }

    });
    dragComponent.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        final boolean deadZone = isWithinDeadZone(e);
        if (!myDragging && !deadZone) {
          myDragging = true;
          myDragJustStarted = true;
        } else if (myDragging) {
          myDragJustStarted = false;
        }

        if (myDragging && myPressPoint != null) {
          final Point draggedTo = new RelativePoint(e).getScreenPoint();
          draggedTo.x -= myPressPoint.x;
          draggedTo.y -= myPressPoint.y;
          processDrag(e, draggedTo);
        }
      }
    });
  }

  protected void processDragFinish(final MouseEvent even) {
  }

  public final boolean isDragJustStarted() {
    return myDragJustStarted;
  }

  protected abstract void processDrag(MouseEvent event, Point dragToScreenPoint);

  protected void mouseClicked(final MouseEvent e) {
  }

  private boolean isWithinDeadZone(final MouseEvent e) {
    return Math.abs(myPressPoint.x - e.getPoint().x) < DRAG_START_DEADZONE && Math.abs(myPressPoint.y - e.getPoint().y) < DRAG_START_DEADZONE;
  }
}
