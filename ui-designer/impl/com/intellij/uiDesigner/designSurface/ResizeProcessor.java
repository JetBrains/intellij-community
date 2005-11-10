package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ResizeProcessor extends EventProcessor {
  private RadComponent myComponent;
  private int myResizeMask;
  private Point myLastPoint;
  private Rectangle myBounds;
  private Rectangle myOriginalBounds;
  private RadContainer myOriginalParent;
  private final GuiEditor myEditor;

  public ResizeProcessor(final GuiEditor editor, final RadComponent component, final int resizeMask){
    myEditor = editor;
    if (component.getParent() == null) {
      throw new IllegalArgumentException("parent is null for " + component);
    }

    myComponent = component;
    myOriginalParent = component.getParent();
    if (component.getParent().isGrid()) {
      Rectangle rc = SwingUtilities.convertRectangle(component.getParent().getDelegee(),
                                                     component.getBounds(),
                                                     myEditor.getDragLayer());
      component.getParent().removeComponent(component);
      component.setBounds(rc);
      editor.getDragLayer().add(component.getDelegee());
    }
    myResizeMask = resizeMask;

    setCursor(getResizeCursor());
  }

  protected void processKeyEvent(final KeyEvent e){}

  protected void processMouseEvent(final MouseEvent e){
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myLastPoint = e.getPoint();
      myBounds = myComponent.getBounds();
      myOriginalBounds = new Rectangle(myBounds);
    }
    else if(e.getID()==MouseEvent.MOUSE_DRAGGED){
      final int dx = e.getX() - myLastPoint.x;
      final int dy = e.getY() - myLastPoint.y;

      final GridConstraints constraints = myComponent.getConstraints();

      if ((myResizeMask & Painter.WEST_MASK) != 0) {
        myBounds.x += dx;
        myBounds.width -= dx;
      }
      if ((myResizeMask & Painter.EAST_MASK) != 0) {
        myBounds.width += dx;
      }
      if ((myResizeMask & Painter.NORTH_MASK) != 0) {
        myBounds.y += dy;
        myBounds.height -= dy;
      }
      if ((myResizeMask & Painter.SOUTH_MASK) != 0) {
        myBounds.height += dy;
      }

      final Dimension minSize = myComponent.getMinimumSize();

      final Rectangle newBounds = myComponent.getBounds();

      // Component's bounds cannot be less the some minimum size
      if (myBounds.width >= minSize.width) {
        newBounds.x = myBounds.x;
        newBounds.width = myBounds.width;
      }
      else {
        if((myResizeMask & Painter.WEST_MASK) != 0){
          newBounds.x = newBounds.x+newBounds.width-minSize.width;
          newBounds.width = minSize.width;
        }
        else if ((myResizeMask & Painter.EAST_MASK) != 0) {
          newBounds.width = minSize.width;
        }
      }

      if (myBounds.height >= minSize.height) {
        newBounds.y = myBounds.y;
        newBounds.height = myBounds.height;
      }
      else {
        if ((myResizeMask & Painter.NORTH_MASK) != 0) {
          newBounds.y = newBounds.y + newBounds.height - minSize.height;
          newBounds.height = minSize.height;
        }
        else if ((myResizeMask & Painter.SOUTH_MASK) != 0) {
          newBounds.height = minSize.height;
        }
      }

      final Dimension size = newBounds.getSize();
      Util.adjustSize(myComponent.getDelegee(), constraints, size);
      newBounds.width = size.width;
      newBounds.height = size.height;

      myComponent.setBounds(newBounds);

      myEditor.refresh();

      myLastPoint=e.getPoint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      if (myOriginalParent.isGrid()) {
        myOriginalParent.addComponent(myComponent);
      }
      myEditor.getActiveDecorationLayer().removeFeedback();
      myEditor.refreshAndSave(true);
    }

  }

  private Cursor getResizeCursor() {
    return Cursor.getPredefinedCursor(Painter.getResizeCursor(myResizeMask));
  }

  protected boolean cancelOperation(){
    myComponent.setBounds(myOriginalBounds);
    if (myOriginalParent != null) {
      myOriginalParent.addComponent(myComponent);
    }
    myEditor.refresh();
    return true;
  }
}
