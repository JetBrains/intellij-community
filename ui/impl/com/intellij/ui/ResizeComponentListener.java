/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * User: anna
 * Date: 13-Mar-2006
 */
public class ResizeComponentListener extends MouseAdapter implements MouseMotionListener {
  private static final int SENSITIVITY = 4;
  private final JComponent myComponent;
  private Point myStartPoint = null;
  private int myDirection = -1;

  public ResizeComponentListener(final JComponent component) {
    myComponent = component;
  }

  public void mousePressed(MouseEvent e) {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow != null) {
      myStartPoint = new RelativePoint(e).getScreenPoint();
      myDirection = getDirection(myStartPoint, popupWindow.getBounds());
      if (myDirection == Cursor.DEFAULT_CURSOR){
        myStartPoint = null;
      }
    }
  }

  public void mouseClicked(MouseEvent e) {
    endOperation();
  }

  public void mouseReleased(MouseEvent e) {
    endOperation();
  }

  private void endOperation() {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow != null) {
      popupWindow.setCursor(Cursor.getDefaultCursor());
    }
    myStartPoint = null;
    myDirection = -1;
  }

  private void doResize(final Point point) {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    final Rectangle bounds = popupWindow.getBounds();
    final Point location = popupWindow.getLocation();
    switch (myDirection){
      case Cursor.NW_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height + myStartPoint.y - point.y );
        break;
      case Cursor.N_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width,
                              bounds.height + myStartPoint.y - point.y);
        break;
      case Cursor.NE_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height + myStartPoint.y - point.y);
        break;
      case Cursor.E_RESIZE_CURSOR :
        popupWindow.setBounds(location.x ,
                              location.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height);
        break;
      case Cursor.SE_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.S_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y,
                              bounds.width ,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.SW_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.W_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height);
        break;
    }
    popupWindow.validate();
    popupWindow.repaint();
  }

  public void mouseMoved(MouseEvent e) {
    Point point = new RelativePoint(e).getScreenPoint();
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow == null) return;
    final int cursor = getDirection(point, popupWindow.getBounds());
    popupWindow.setCursor(Cursor.getPredefinedCursor(cursor));
    if (cursor != Cursor.DEFAULT_CURSOR){
      e.consume();
    }
  }

  public void mouseDragged(MouseEvent e) {
    if (e.isConsumed()) return;
    final Point point = new RelativePoint(e).getScreenPoint();
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow == null) return;
    if (myStartPoint != null) {
      popupWindow.setCursor(Cursor.getPredefinedCursor(myDirection));
      doResize(point);
      myStartPoint = point;
      e.consume();
    } else {
       final int cursor = getDirection(point, popupWindow.getBounds());
       popupWindow.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  }

  private static int getDirection(Point startPoint, Rectangle bounds){
    bounds = new Rectangle(bounds.x + 2, bounds.y + 2, bounds.width - 6, bounds.height - 6);
    if (!bounds.contains(startPoint)){
      return Cursor.DEFAULT_CURSOR;
    }
    if (Math.abs(startPoint.x - bounds.x ) < SENSITIVITY){ //left bound
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.NW_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.SW_RESIZE_CURSOR;
      } else { //edge
        return Cursor.W_RESIZE_CURSOR;
      }
    } else if (Math.abs(bounds.x + bounds.width - startPoint.x) < SENSITIVITY){ //right
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.NE_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.SE_RESIZE_CURSOR;
      } else { //edge
        return Cursor.E_RESIZE_CURSOR;
      }
    } else { //other
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.N_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.S_RESIZE_CURSOR;
      } else { //edge
        return Cursor.DEFAULT_CURSOR;
      }
    }
  }
}
