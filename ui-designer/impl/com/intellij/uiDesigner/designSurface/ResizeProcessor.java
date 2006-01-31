package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.propertyInspector.properties.PreferredSizeProperty;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ResizeProcessor extends EventProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.ResizeProcessor");

  private RadComponent myComponent;
  private int myResizeMask;
  private Point myLastPoint;
  private Point myPressPoint;
  private Rectangle myBounds;
  private Rectangle myOriginalBounds;
  private RadContainer myOriginalParent;
  private final GuiEditor myEditor;
  private PreferredSizeProperty myPreferredSizeProperty = new PreferredSizeProperty();
  private boolean doResize = false;

  public ResizeProcessor(final GuiEditor editor, final RadComponent component, final int resizeMask){
    myEditor = editor;
    if (component.getParent() == null) {
      throw new IllegalArgumentException("parent is null for " + component);
    }

    myComponent = component;
    myComponent.setResizing(true);
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
      myPressPoint = myLastPoint;
      myBounds = myComponent.getBounds();
      myOriginalBounds = new Rectangle(myBounds);
    }
    else if(e.getID()==MouseEvent.MOUSE_DRAGGED){
      final int dx = e.getX() - myLastPoint.x;
      final int dy = e.getY() - myLastPoint.y;

      if ((Math.abs(e.getX() - myPressPoint.getX()) > DragSelectionProcessor.TREMOR ||
           Math.abs(e.getY() - myPressPoint.getY()) > DragSelectionProcessor.TREMOR)) {
        doResize = true;
      }

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
        if (doResize) {
          Dimension preferredSize = (Dimension) myPreferredSizeProperty.getValue(myComponent);
          if ((myResizeMask & (Painter.WEST_MASK | Painter.EAST_MASK)) != 0) {
            preferredSize.width= myComponent.getWidth();
          }
          if ((myResizeMask & (Painter.NORTH_MASK | Painter.SOUTH_MASK)) != 0) {
            preferredSize.height= myComponent.getHeight();
          }
          try {
            myPreferredSizeProperty.setValue(myComponent, preferredSize);
          }
          catch (Exception e1) {
            LOG.error(e1);
          }
        }
        myOriginalParent.addComponent(myComponent);
      }
      myComponent.setResizing(false);
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
    myComponent.setResizing(false);
    myEditor.refresh();
    return true;
  }
}
