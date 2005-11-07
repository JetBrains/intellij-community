package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2005
 * Time: 20:10:59
 * To change this template use File | Settings | File Templates.
 */
public class GridInsertProcessor {
  private static final int INSERT_ARROW_SIZE = 3;

  private static class InsertFeedbackPainter extends JPanel {
    private boolean myVertical = false;
    private boolean myWholeCell = false;

    public InsertFeedbackPainter() {
      setOpaque(false);
    }

    public void setVertical(final boolean vertical) {
      myVertical = vertical;
    }

    public void setWholeCell(final boolean wholeCell) {
      myWholeCell = wholeCell;
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;
      final Stroke savedStroke = g2d.getStroke();
      final Color savedColor = g2d.getColor();
      try {
        g2d.setColor(Color.BLUE);
        if (myWholeCell) {
          g2d.setStroke(new BasicStroke(2.5f));
          // give space for stroke to be painted
          g2d.drawRect(1, 1, getWidth()-2, getHeight()-2);
        }
        else if (myVertical) {
          g2d.setStroke(new BasicStroke(1.5f));
          int midX = getWidth()/2;
          g2d.drawLine(0, 0, midX, INSERT_ARROW_SIZE);
          g2d.drawLine(getWidth()-1, 0, midX, INSERT_ARROW_SIZE);
          g2d.drawLine(midX, INSERT_ARROW_SIZE, midX, getHeight()-INSERT_ARROW_SIZE);
          g2d.drawLine(0, getHeight()-1, midX, getHeight()-INSERT_ARROW_SIZE);
          g2d.drawLine(getWidth()-1, getHeight()-1, midX, getHeight()-INSERT_ARROW_SIZE);
        }
        else {
          g2d.setStroke(new BasicStroke(1.5f));
          int midY = getHeight()/2;
          g2d.drawLine(0, 0, INSERT_ARROW_SIZE, midY);
          g2d.drawLine(0, getHeight()-1, INSERT_ARROW_SIZE, midY);
          g2d.drawLine(INSERT_ARROW_SIZE, midY, getWidth()-INSERT_ARROW_SIZE, midY);
          g2d.drawLine(getWidth()-1, 0, getWidth()-INSERT_ARROW_SIZE, midY);
          g2d.drawLine(getWidth()-1, getHeight()-1, getWidth()-INSERT_ARROW_SIZE, midY);
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

  GridInsertLocation getGridInsertLocation(final int x, final int y, final int dragColumnDelta) {
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

    int[] horzGridLines = grid.getHorizontalGridLines();
    int[] vertGridLines = grid.getVerticalGridLines();

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

    Rectangle cellRect = new Rectangle(vertGridLines [col],
                                       horzGridLines [row],
                                       vertGridLines [col+1]-vertGridLines [col],
                                       horzGridLines [row+1]-horzGridLines [row]);
    // if a number of adjacent components have been selected and the component being dragged
    // is not the leftmost, we return the column in which the leftmost component should be dropped
    if ((mode == GridInsertMode.RowBefore || mode == GridInsertMode.RowAfter) &&
        col >= dragColumnDelta) {
      col -= dragColumnDelta;
    }
    return new GridInsertLocation(container, row, col, cellRect, mode);
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

  public int processDragEvent(int x, int y, final boolean copyOnDrop, int componentCount,
                              final int dragColumnDelta) {
    final GridInsertLocation insertLocation = getGridInsertLocation(x, y, dragColumnDelta);
    if (insertLocation.getContainer() != null) {
      if (isDropInsertAllowed(insertLocation, componentCount)) {
        placeInsertFeedbackPainter(insertLocation, componentCount);
        if (myInsertFeedbackPainter.getParent() == null) {
          myEditor.getActiveDecorationLayer().add(myInsertFeedbackPainter);
        }
        myEditor.getActiveDecorationLayer().repaint();
        return copyOnDrop ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE;
      }
    }
    removeFeedbackPainter();
    if (FormEditingUtil.canDrop(myEditor, x, y, componentCount)) {
      return copyOnDrop ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE;
    }
    return DnDConstants.ACTION_NONE;
  }

  public Cursor processMouseMoveEvent(final int x, final int y, final boolean copyOnDrop,
                                      final int componentCount, final int dragColumnDelta) {
    int operation = processDragEvent(x, y, copyOnDrop, componentCount, dragColumnDelta);
    switch(operation) {
      case DnDConstants.ACTION_COPY: return FormEditingUtil.getCopyDropCursor();
      case DnDConstants.ACTION_MOVE: return FormEditingUtil.getMoveDropCursor();
      default: return FormEditingUtil.getMoveNoDropCursor();
    }
  }


  public boolean isDropInsertAllowed(final GridInsertLocation insertLocation, final int componentCount) {
    if (insertLocation == null || insertLocation.getContainer() == null) {
      return false;
    }
    if (insertLocation.getMode() == GridInsertMode.None) {
      return componentCount == 1 &&
             insertLocation.getContainer().getComponentAtGrid(insertLocation.getRow(), insertLocation.getColumn()) == null;
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
      cellRect.setBounds(xs [insertLocation.getColumn()], (int) cellRect.getY(),
                         xs [lastCol] + widths [lastCol] - xs [insertLocation.getColumn()], (int)cellRect.getHeight());
    }
    cellRect = SwingUtilities.convertRectangle(insertLocation.getContainer().getDelegee(),
                                               cellRect,
                                               myEditor.getActiveDecorationLayer());

    if (insertLocation.getMode() == GridInsertMode.None) {
      myInsertFeedbackPainter.setWholeCell(true);
      myInsertFeedbackPainter.setBounds(cellRect);
      }
    else {
      myInsertFeedbackPainter.setWholeCell(false);
      myInsertFeedbackPainter.setVertical(insertLocation.getMode() == GridInsertMode.ColumnBefore ||
                                          insertLocation.getMode() == GridInsertMode.ColumnAfter);

      int w=4;
      switch(insertLocation.getMode()) {
        case ColumnBefore:
          myInsertFeedbackPainter.setBounds((int) cellRect.getMinX()-w, (int) cellRect.getMinY()-INSERT_ARROW_SIZE,
                                            2*w, (int) cellRect.getHeight()+2*INSERT_ARROW_SIZE);
          break;

        case ColumnAfter:
          myInsertFeedbackPainter.setBounds((int) cellRect.getMaxX()-w, (int) cellRect.getMinY()-INSERT_ARROW_SIZE,
                                            2*w, (int) cellRect.getHeight()+2*INSERT_ARROW_SIZE);
          break;

        case RowBefore:
          myInsertFeedbackPainter.setBounds((int) cellRect.getMinX()-INSERT_ARROW_SIZE, (int) cellRect.getMinY()-w,
                                            (int) cellRect.getWidth()+2*INSERT_ARROW_SIZE, 2*w);
          break;

        case RowAfter:
          myInsertFeedbackPainter.setBounds((int) cellRect.getMinX()-INSERT_ARROW_SIZE, (int) cellRect.getMaxY()-w,
                                            (int) cellRect.getWidth()+2*INSERT_ARROW_SIZE, 2*w);
          break;
      }
    }
  }
}
