package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ResizeProcessor extends EventProcessor{
  private RadComponent myComponent;
  private int myResizeMask;
  private Point myLastPoint;
  private Rectangle myBounds;
  private GridConstraints myOriginalConstraints;
  private Rectangle myOriginalBounds;
  private RadContainer myOriginalParent;
  private final GuiEditor myEditor;
  private static final int EPSILON = 5;

  public ResizeProcessor(final GuiEditor editor, final RadComponent component, final int resizeMask){
    myEditor = editor;
    if (component.getParent() == null) {
      throw new IllegalArgumentException("parent is null for " + component);
    }

    myComponent = component;
    myOriginalParent = component.getParent();
    myOriginalConstraints = component.getConstraints();
    if (!component.getParent().isXY()) {
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

      if (!myOriginalParent.isXY()) {
        final Point point = SwingUtilities.convertPoint(myEditor.getDragLayer(), e.getX(), e.getY(), myOriginalParent.getDelegee());
        putGridSpanFeedback(point);
      }
      else {
        myEditor.getActiveDecorationLayer().removeFeedback();
        setCursor(getResizeCursor());
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
      if (!myOriginalParent.isXY()) {
        final Point point = SwingUtilities.convertPoint(myEditor.getDragLayer(), e.getX(), e.getY(), myOriginalParent.getDelegee());
        final GridLayoutManager grid = (GridLayoutManager)myOriginalParent.getLayout();
        Rectangle rcGrid = getGridSpanGridRect(grid, myOriginalConstraints, point, myResizeMask);
        if (rcGrid != null && isGridSpanDropAllowed(rcGrid)) {
          myOriginalConstraints.setColumn(rcGrid.x);
          myOriginalConstraints.setRow(rcGrid.y);
          myOriginalConstraints.setColSpan(rcGrid.width);
          myOriginalConstraints.setRowSpan(rcGrid.height);
        }
        myOriginalParent.addComponent(myComponent);
      }
      myEditor.getActiveDecorationLayer().removeFeedback();
      myEditor.refreshAndSave(true);
    }

  }

  private Cursor getResizeCursor() {
    return Cursor.getPredefinedCursor(Painter.getResizeCursor(myResizeMask));
  }

  private void putGridSpanFeedback(final Point point) {
    final GridLayoutManager grid = (GridLayoutManager)myOriginalParent.getLayout();
    Rectangle rcGrid = getGridSpanGridRect(grid, myOriginalConstraints, point, myResizeMask);
    if (rcGrid != null) {
      Rectangle rc = grid.getCellRangeRect(rcGrid.y, rcGrid.x, rcGrid.y+rcGrid.height-1, rcGrid.x+rcGrid.width-1);
      rc = SwingUtilities.convertRectangle(myOriginalParent.getDelegee(), rc, myEditor.getActiveDecorationLayer());
      myEditor.getActiveDecorationLayer().putFeedback(rc);
      setCursor(isGridSpanDropAllowed(rcGrid) ? getResizeCursor() : FormEditingUtil.getMoveNoDropCursor());
    }
    else {
      setCursor(getResizeCursor());
      myEditor.getActiveDecorationLayer().removeFeedback();
    }
  }

  static Rectangle getGridSpanGridRect(final GridLayoutManager grid,
                                       final GridConstraints originalConstraints,
                                       final Point point,
                                       final int resizeMask) {
    int horzGridLine = (resizeMask & (Painter.NORTH_MASK | Painter.SOUTH_MASK)) != 0
                       ? grid.getHorizontalGridLineNear(point.y, EPSILON)
                       : -1;
    int vertGridLine = (resizeMask & (Painter.WEST_MASK | Painter.EAST_MASK)) != 0
                       ? grid.getVerticalGridLineNear(point.x, EPSILON)
                       : -1;
    if (horzGridLine != -1 || vertGridLine != -1) {
      final int origStartCol = originalConstraints.getColumn();
      final int origEndCol = originalConstraints.getColumn() + originalConstraints.getColSpan() - 1;
      int startCol = origStartCol;
      int endCol = origEndCol;
      if (vertGridLine >= 0) {
        if ((resizeMask & Painter.WEST_MASK) != 0 && vertGridLine <= endCol) {
          // resize to left
          startCol = vertGridLine;
        }
        else if ((resizeMask & Painter.EAST_MASK) != 0 && vertGridLine > startCol) {
          endCol = vertGridLine-1;
        }
      }

      final int origStartRow = originalConstraints.getRow();
      final int origEndRow = originalConstraints.getRow() + originalConstraints.getRowSpan() - 1;
      int startRow = origStartRow;
      int endRow = origEndRow;
      if (horzGridLine >= 0) {
        if ((resizeMask & Painter.NORTH_MASK) != 0 && horzGridLine <= endRow) {
          startRow = horzGridLine;
        }
        else if ((resizeMask & Painter.SOUTH_MASK) != 0 && horzGridLine > startRow) {
          endRow = horzGridLine-1;
        }
      }

      return new Rectangle(startCol, startRow, endCol-startCol+1, endRow-startRow+1);
    }
    return null;
  }

  protected boolean cancelOperation(){
    myComponent.setBounds(myOriginalBounds);
    if (myOriginalParent != null) {
      myOriginalParent.addComponent(myComponent);
    }
    myEditor.refresh();
    return true;
  }

  private boolean isGridSpanDropAllowed(final Rectangle rcGrid) {
    for(int row=rcGrid.y; row < rcGrid.y+rcGrid.height; row++) {
      for(int col=rcGrid.x; col < rcGrid.x+rcGrid.width; col++) {
        if (myOriginalParent.getComponentAtGrid(row, col) != null) {
          return false;
        }
      }
    }
    return true;
  }
}
