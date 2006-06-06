package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ResizeProcessor extends EventProcessor {
  private RadComponent myComponent;
  private int myResizeMask;
  private Point myLastPoint;
  private Point myPressPoint;
  private Rectangle myBounds;
  private Rectangle myOriginalBounds;
  private RadContainer myOriginalParent;
  private final GuiEditor myEditor;
  private GridConstraints myOriginalConstraints;
  private RadComponent myResizedCopy;

  public ResizeProcessor(final GuiEditor editor, final RadComponent component, final int resizeMask){
    myEditor = editor;
    if (component.getParent() == null) {
      throw new IllegalArgumentException("parent is null for " + component);
    }

    myComponent = component;
    myOriginalParent = component.getParent();
    myOriginalConstraints = component.getConstraints();

    final List<RadComponent> copyList = CutCopyPasteSupport.copyComponents(editor, Collections.singletonList(component));
    if (component.getParent().getLayoutManager().isGrid() && copyList != null) {
      myComponent.setResizing(true);
      Rectangle rc = SwingUtilities.convertRectangle(component.getParent().getDelegee(),
                                                     component.getBounds(),
                                                     myEditor.getDragLayer());
      component.setDragging(true);
      component.setSelected(false);

      myResizedCopy = copyList.get(0);
      myResizedCopy.setBounds(rc);
      myResizedCopy.setSelected(true);
      editor.getDragLayer().add(myResizedCopy.getDelegee());
    }
    myResizeMask = resizeMask;

    setCursor(getResizeCursor());
  }

  protected void processKeyEvent(final KeyEvent e){}

  protected void processMouseEvent(final MouseEvent e){
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myLastPoint = e.getPoint();
      myPressPoint = myLastPoint;
      myBounds = myOriginalParent.getLayoutManager().isGrid() ? myResizedCopy.getBounds() : myComponent.getBounds();
      myOriginalBounds = new Rectangle(myBounds);
    }
    else if(e.getID()==MouseEvent.MOUSE_DRAGGED){
      final int dx = e.getX() - myLastPoint.x;
      final int dy = e.getY() - myLastPoint.y;

      if (myOriginalParent.getLayoutManager().isGrid()) {
        final Point point = SwingUtilities.convertPoint(myEditor.getDragLayer(), e.getX(), e.getY(), myOriginalParent.getDelegee());
        putGridSpanFeedback(point);
      }
      else if (myOriginalParent.isXY()) {
        myEditor.getActiveDecorationLayer().removeFeedback();
        setCursor(getResizeCursor());
      }
      else {
        return;
      }

      if ((Math.abs(e.getX() - myPressPoint.getX()) > DragSelectionProcessor.TREMOR ||
           Math.abs(e.getY() - myPressPoint.getY()) > DragSelectionProcessor.TREMOR)) {
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

      final Rectangle newBounds = myOriginalParent.getLayoutManager().isGrid() ? myResizedCopy.getBounds() : myComponent.getBounds();

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

      if (myOriginalParent.getLayoutManager().isGrid()) {
        myResizedCopy.setBounds(newBounds);
      }
      else {
        if (myEditor.ensureEditable()) {
          myComponent.setBounds(newBounds);
        }
      }

      myEditor.refresh();

      myLastPoint=e.getPoint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      boolean modified = false;
      myComponent.getDelegee().setVisible(true);
      myComponent.setResizing(false);
      myComponent.setSelected(true);
      if (myResizedCopy != null) {
        myEditor.getDragLayer().remove(myResizedCopy.getDelegee());
      }
      if (myOriginalParent.getLayoutManager().isGrid() && myEditor.ensureEditable()) {
        final Point point = SwingUtilities.convertPoint(myEditor.getDragLayer(), e.getX(), e.getY(), myOriginalParent.getDelegee());
        Rectangle rcGrid = getGridSpanGridRect(myOriginalParent, myOriginalConstraints, point, myResizeMask);
        if (rcGrid != null && isGridSpanDropAllowed(rcGrid)) {
          GridConstraints oldConstraints = (GridConstraints) myOriginalConstraints.clone();
          myOriginalConstraints.setColumn(rcGrid.x);
          myOriginalConstraints.setRow(rcGrid.y);
          myOriginalConstraints.setColSpan(rcGrid.width);
          myOriginalConstraints.setRowSpan(rcGrid.height);
          myComponent.fireConstraintsChanged(oldConstraints);
          modified = true;
        }
      }
      myEditor.getActiveDecorationLayer().removeFeedback();
      myComponent.setDragging(false);
      if (modified) {
        myEditor.refreshAndSave(true);
      }
    }
  }

  private Cursor getResizeCursor() {
    return Cursor.getPredefinedCursor(Painter.getResizeCursor(myResizeMask));
  }

  private void putGridSpanFeedback(final Point point) {
    Rectangle rcGrid = getGridSpanGridRect(myOriginalParent, myOriginalConstraints, point, myResizeMask);
    if (rcGrid != null) {
      Rectangle rc = myOriginalParent.getGridLayoutManager().getGridCellRangeRect(myOriginalParent, rcGrid.y, rcGrid.x,
                                                                                  rcGrid.y+rcGrid.height-1, rcGrid.x+rcGrid.width-1);
      String tooltip = UIDesignerBundle.message("resize.feedback", rcGrid.height, rcGrid.width);
      myEditor.getActiveDecorationLayer().putFeedback(myOriginalParent.getDelegee(), rc, tooltip);
      setCursor(isGridSpanDropAllowed(rcGrid) ? getResizeCursor() : FormEditingUtil.getMoveNoDropCursor());
    }
    else {
      setCursor(getResizeCursor());
      myEditor.getActiveDecorationLayer().removeFeedback();
    }
  }

  @Nullable
  static Rectangle getGridSpanGridRect(final RadContainer grid,
                                       final GridConstraints originalConstraints,
                                       final Point point,
                                       final int resizeMask) {
    int rowAtMouse = (resizeMask & (Painter.NORTH_MASK | Painter.SOUTH_MASK)) != 0
                     ? grid.getGridRowAt(point.y)
                     : -1;
    int colAtMouse = (resizeMask & (Painter.WEST_MASK | Painter.EAST_MASK)) != 0
                     ? grid.getGridColumnAt(point.x)
                     : -1;
    if (rowAtMouse != -1 || colAtMouse != -1) {
      final int origStartCol = originalConstraints.getColumn();
      final int origEndCol = originalConstraints.getColumn() + originalConstraints.getColSpan() - 1;
      int startCol = origStartCol;
      int endCol = origEndCol;
      if (colAtMouse >= 0) {
        if ((resizeMask & Painter.WEST_MASK) != 0 && colAtMouse <= endCol) {
          // resize to left
          startCol = colAtMouse;
        }
        else if ((resizeMask & Painter.EAST_MASK) != 0 && colAtMouse >= startCol) {
          endCol = colAtMouse;
        }
      }

      final int origStartRow = originalConstraints.getRow();
      final int origEndRow = originalConstraints.getRow() + originalConstraints.getRowSpan() - 1;
      int startRow = origStartRow;
      int endRow = origEndRow;
      if (rowAtMouse >= 0) {
        if ((resizeMask & Painter.NORTH_MASK) != 0 && rowAtMouse <= endRow) {
          startRow = rowAtMouse;
        }
        else if ((resizeMask & Painter.SOUTH_MASK) != 0 && rowAtMouse >= startRow) {
          endRow = rowAtMouse;
        }
      }

      return new Rectangle(startCol, startRow, endCol-startCol+1, endRow-startRow+1);
    }
    return null;
  }

  protected boolean cancelOperation(){
    myComponent.setBounds(myOriginalBounds);
    myComponent.setResizing(false);
    myComponent.setDragging(false);
    if (myResizedCopy != null) {
      myEditor.getDragLayer().remove(myResizedCopy.getDelegee());
      myResizedCopy = null;
    }
    myEditor.refresh();
    return true;
  }

  private boolean isGridSpanDropAllowed(final Rectangle rcGrid) {
    return myOriginalParent.findComponentInRect(rcGrid.y, rcGrid.x, rcGrid.height, rcGrid.width) == null;
  }
}
