package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2005
 * Time: 20:10:59
 * To change this template use File | Settings | File Templates.
 */
public class GridInsertProcessor {
  private static class InsertFeedbackPainter extends JPanel {
    private boolean myVertical = false;

    public InsertFeedbackPainter() {
      setOpaque(false);
    }

    public void setVertical(final boolean vertical) {
      myVertical = vertical;
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;
      final Stroke savedStroke = g2d.getStroke();
      final Color savedColor = g2d.getColor();
      try {
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(Color.BLUE);
        int delta = 3;
        if (myVertical) {
          int midX = getWidth()/2;
          g2d.drawLine(0, 0, midX, delta);
          g2d.drawLine(getWidth()-1, 0, midX, delta);
          g2d.drawLine(midX, delta, midX, getHeight()-delta);
          g2d.drawLine(0, getHeight()-1, midX, getHeight()-delta);
          g2d.drawLine(getWidth()-1, getHeight()-1, midX, getHeight()-delta);
        }
        else {
          int midY = getHeight()/2;
          g2d.drawLine(0, 0, delta, midY);
          g2d.drawLine(0, getHeight()-1, delta, midY);
          g2d.drawLine(delta, midY, getWidth()-delta, midY);
          g2d.drawLine(getWidth()-1, 0, getWidth()-delta, midY);
          g2d.drawLine(getWidth()-1, getHeight()-1, getWidth()-delta, midY);
        }
      }
      finally {
        g2d.setStroke(savedStroke);
        g2d.setColor(savedColor);
      }
    }
  }

  private InsertFeedbackPainter myInsertFeedbackPainter = new InsertFeedbackPainter();

  enum GridInsertMode { None, RowBefore, RowAfter, ColumnBefore, ColumnAfter }

  class GridInsertLocation {
    private RadContainer myContainer;
    private int myRow;
    private int myColumn;
    private Rectangle myCellRect;
    private GridInsertMode myMode;

    public GridInsertLocation(final GridInsertMode mode) {
      myMode = mode;
    }

    public GridInsertLocation(final RadContainer container,
                              final int row,
                              final int column,
                              final Rectangle cellRect,
                              final GridInsertMode mode) {
      myContainer = container;
      myRow = row;
      myColumn = column;
      myCellRect = cellRect;
      myMode = mode;
    }

    public GridInsertMode getMode() {
      return myMode;
    }

    public int getRow() {
      return myRow;
    }

    public int getColumn() {
      return myColumn;
    }

    public RadContainer getContainer() {
      return myContainer;
    }

    public Rectangle getCellRect() {
      return myCellRect;
    }
  }

  private GuiEditor myEditor;

  public GridInsertProcessor(final GuiEditor editor) {
    myEditor = editor;
  }

  GridInsertLocation getGridInsertLocation(final int x, final int y) {
    final int EPSILON = 4;
    final RadContainer container = FormEditingUtil.getRadContainerAt(myEditor, x, y, EPSILON);
    if (container == null || !container.isGrid()) {
      return new GridInsertLocation(GridInsertMode.None);
    }
    final GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    final Point targetPoint = SwingUtilities.convertPoint(myEditor.getDragLayer(),
                                                          x, y, container.getDelegee());
    int[] xs = grid.getXs();
    int[] ys = grid.getYs();
    int[] widths = grid.getWidths();
    int[] heights = grid.getHeights();

    int row=ys.length-1, col=xs.length-1;
    for(int i=0; i<xs.length; i++) {
      if (targetPoint.getX() < xs [i]+widths [i]) {
        col=i;
        break;
      }
    }
    for(int i=0; i<ys.length; i++) {
      if (targetPoint.getY() < ys [i]+heights [i]) {
        row=i;
        break;
      }
    }

    GridInsertMode mode = GridInsertMode.None;

    int dx = (int)(targetPoint.getX() - xs [col]);
    if (dx < EPSILON) {
      mode = GridInsertMode.ColumnBefore;
    }
    else if (widths [col] - dx < EPSILON) {
      mode = GridInsertMode.ColumnAfter;
    }

    int dy = (int)(targetPoint.getY() - ys [row]);
    if (dy < EPSILON) {
      mode = GridInsertMode.RowBefore;
    }
    else if (heights [row] - dy < EPSILON) {
      mode = GridInsertMode.RowAfter;
    }
    if (mode != GridInsertMode.None) {
      Rectangle cellRect = new Rectangle(xs [col], ys [row], widths [col], heights [row]);
      return new GridInsertLocation(container, row, col, cellRect, mode);
    }
    return new GridInsertLocation(GridInsertMode.None);
  }

  @Nullable
  DropInfo processGridInsertOnDrop(final GridInsertLocation location,
                                   final RadComponent[] insertedComponents,
                                   final GridConstraints[] constraintsToAdjust) {
    int row = location.getRow();
    int col = location.getColumn();
    RadContainer container = location.getContainer();
    switch(location.getMode()) {
      case RowBefore:
        GridChangeUtil.insertRowBefore(container, row);
        checkAdjustConstraints(constraintsToAdjust, true, row);
        break;

      case RowAfter:
        GridChangeUtil.insertRowAfter(container, row);
        row++;
        checkAdjustConstraints(constraintsToAdjust, true, row);
        break;

      case ColumnBefore:
        GridChangeUtil.insertColumnBefore(container, col);
        checkAdjustConstraints(constraintsToAdjust, false, col);
        break;

      case ColumnAfter:
        GridChangeUtil.insertColumnAfter(container, col);
        col++;
        checkAdjustConstraints(constraintsToAdjust, false, col);
        break;
    }
    return container.dropIntoGrid(insertedComponents, row, col);
  }

  private void checkAdjustConstraints(final GridConstraints[] constraintsToAdjust,
                                      final boolean isRow,
                                      final int index
  ) {
    if (constraintsToAdjust != null) {
      for(GridConstraints constraints: constraintsToAdjust) {
        GridChangeUtil.adjustConstraintsOnInsert(constraints, isRow, index);
      }
    }
  }

  public Cursor processMouseMoveEvent(int x, int y, int componentCount, final boolean copyOnDrop) {
    final GridInsertLocation insertLocation = getGridInsertLocation(x, y);
    if (insertLocation.getMode() != GridInsertMode.None) {
      if (isDropInsertAllowed(insertLocation, componentCount)) {
        placeInsertFeedbackPainter(insertLocation, componentCount);
        if (myInsertFeedbackPainter.getParent() == null) {
          myEditor.getActiveDecorationLayer().add(myInsertFeedbackPainter);
        }
        myEditor.getActiveDecorationLayer().repaint();
        return copyOnDrop ? FormEditingUtil.getCopyDropCursor() : FormEditingUtil.getMoveDropCursor();
      }
    }
    removeFeedbackPainter();
    if (FormEditingUtil.canDrop(myEditor, x, y, componentCount)) {
      return copyOnDrop ? FormEditingUtil.getCopyDropCursor() : FormEditingUtil.getMoveDropCursor();
    }
    return FormEditingUtil.getMoveNoDropCursor();
  }

  public boolean isDropInsertAllowed(final GridInsertLocation insertLocation, final int componentCount) {
    if (insertLocation == null || insertLocation.getMode() == GridInsertMode.None) {
      return false;
    }
    final GridLayoutManager grid = ((GridLayoutManager)insertLocation.getContainer().getLayout());
    return insertLocation.getColumn() + componentCount - 1 < grid.getColumnCount();
  }

  void removeFeedbackPainter() {
    if (myInsertFeedbackPainter.getParent() == myEditor.getActiveDecorationLayer()) {
      myEditor.getActiveDecorationLayer().remove(myInsertFeedbackPainter);
      myEditor.getActiveDecorationLayer().repaint();
    }
  }

  private void placeInsertFeedbackPainter(final GridInsertLocation insertLocation, final int componentCount) {
    Rectangle cellRect = insertLocation.getCellRect();
    if (componentCount > 1) {
      int lastCol = insertLocation.getColumn() + componentCount - 1;
      final GridLayoutManager layoutManager = (GridLayoutManager) insertLocation.getContainer().getLayout();
      int[] xs = layoutManager.getXs();
      int[] widths = layoutManager.getWidths();
      cellRect.setSize(xs [lastCol] + widths [lastCol] - xs [insertLocation.getColumn()], (int)cellRect.getHeight());
    }
    cellRect = SwingUtilities.convertRectangle(insertLocation.getContainer().getDelegee(),
                                               cellRect,
                                               myEditor.getActiveDecorationLayer());
    cellRect.grow(3, 3);
    myInsertFeedbackPainter.setVertical(insertLocation.getMode() == GridInsertMode.ColumnBefore ||
                                        insertLocation.getMode() == GridInsertMode.ColumnAfter);

    int w=4;
    switch(insertLocation.getMode()) {
      case ColumnBefore:
        myInsertFeedbackPainter.setBounds((int) cellRect.getMinX()-w, (int) cellRect.getMinY(),
                                          2*w, (int) cellRect.getHeight());
        break;

      case ColumnAfter:
        myInsertFeedbackPainter.setBounds((int) cellRect.getMaxX()-w, (int) cellRect.getMinY(),
                                          2*w, (int) cellRect.getHeight());
        break;

      case RowBefore:
        myInsertFeedbackPainter.setBounds((int) cellRect.getMinX(), (int) cellRect.getMinY()-w,
                                          (int) cellRect.getWidth(), 2*w);
        break;

      case RowAfter:
        myInsertFeedbackPainter.setBounds((int) cellRect.getMinX(), (int) cellRect.getMaxY()-w,
                                          (int) cellRect.getWidth(), 2*w);
        break;
    }
  }
}
