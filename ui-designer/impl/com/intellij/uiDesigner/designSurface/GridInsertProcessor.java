package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2005
 * Time: 20:10:59
 * To change this template use File | Settings | File Templates.
 */
public class GridInsertProcessor {
  private static final int INSERT_ARROW_SIZE = 3;

  private static class HorzInsertFeedbackPainter implements FeedbackPainter {
    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(1.5f));
      int midY = (int)rc.getCenterY();
      int right = rc.x + rc.width - 1;
      int bottom = rc.y + rc.height - 1;
      g2d.drawLine(rc.x, rc.y, GridInsertProcessor.INSERT_ARROW_SIZE, midY);
      g2d.drawLine(rc.x, bottom, GridInsertProcessor.INSERT_ARROW_SIZE, midY);
      g2d.drawLine(GridInsertProcessor.INSERT_ARROW_SIZE, midY,
                   right - GridInsertProcessor.INSERT_ARROW_SIZE, midY);
      g2d.drawLine(right, rc.y,
                   rc.x+rc.width-GridInsertProcessor.INSERT_ARROW_SIZE, midY);
      g2d.drawLine(right, bottom,
                   right-GridInsertProcessor.INSERT_ARROW_SIZE, midY);
    }
  }

  private static class VertInsertFeedbackPainter implements FeedbackPainter {
    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(1.5f));
      int right = rc.x + rc.width - 1;
      int bottom = rc.y + rc.height - 1;
      int midX = (int) rc.getCenterX();
      g2d.drawLine(rc.x, rc.y, midX, rc.y+GridInsertProcessor.INSERT_ARROW_SIZE);
      g2d.drawLine(right, rc.y, midX, rc.y+GridInsertProcessor.INSERT_ARROW_SIZE);
      g2d.drawLine(midX, rc.y+GridInsertProcessor.INSERT_ARROW_SIZE,
                   midX, bottom-GridInsertProcessor.INSERT_ARROW_SIZE);
      g2d.drawLine(rc.x, bottom, midX, bottom-GridInsertProcessor.INSERT_ARROW_SIZE);
      g2d.drawLine(right, bottom, midX, bottom-GridInsertProcessor.INSERT_ARROW_SIZE);
    }
  }

  private GuiEditor myEditor;
  private FeedbackPainter myHorzInsertFeedbackPainter = new HorzInsertFeedbackPainter();
  private FeedbackPainter myVertInsertFeedbackPainter = new VertInsertFeedbackPainter();

  public GridInsertProcessor(final GuiEditor editor) {
    myEditor = editor;
  }

  static GridInsertLocation getGridInsertLocation(final GuiEditor editor, final int x, final int y, final int dragColumnDelta) {
    int EPSILON = 4;
    RadContainer container = FormEditingUtil.getRadContainerAt(editor, x, y, EPSILON);
    // to facilitate initial component adding, increase stickiness if there is one container at top level
    if (container instanceof RadRootContainer && editor.getRootContainer().getComponentCount() == 1) {
      final RadComponent singleComponent = editor.getRootContainer().getComponents()[0];
      if (singleComponent instanceof RadContainer) {
        Rectangle rc = singleComponent.getDelegee().getBounds();
        rc.grow(EPSILON*2, EPSILON*2);
        final Point pnt = SwingUtilities.convertPoint(editor.getDragLayer(),
                                                              x, y, editor.getRootContainer().getDelegee());
        if (rc.contains(pnt)) {
          container = (RadContainer) singleComponent;
          EPSILON *= 2;
        }
      }
    }

    if (container == null || !container.isGrid()) {
      return new GridInsertLocation(GridInsertLocation.GridInsertMode.None);
    }
    final GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    final Point targetPoint = SwingUtilities.convertPoint(editor.getDragLayer(),
                                                          x, y, container.getDelegee());
    int[] xs = grid.getXs();
    int[] ys = grid.getYs();
    int[] widths = grid.getWidths();
    int[] heights = grid.getHeights();

    int[] horzGridLines = grid.getHorizontalGridLines();
    int[] vertGridLines = grid.getVerticalGridLines();

    int row=ys.length-1;
    int col=xs.length-1;
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

    GridInsertLocation.GridInsertMode mode = GridInsertLocation.GridInsertMode.None;

    int dx = (int)(targetPoint.getX() - xs [col]);
    if (dx < EPSILON) {
      mode = GridInsertLocation.GridInsertMode.ColumnBefore;
    }
    else if (widths [col] - dx < EPSILON) {
      mode = GridInsertLocation.GridInsertMode.ColumnAfter;
    }

    int dy = (int)(targetPoint.getY() - ys [row]);
    if (dy < EPSILON) {
      mode = GridInsertLocation.GridInsertMode.RowBefore;
    }
    else if (heights [row] - dy < EPSILON) {
      mode = GridInsertLocation.GridInsertMode.RowAfter;
    }

    Rectangle cellRect = new Rectangle(vertGridLines [col],
                                       horzGridLines [row],
                                       vertGridLines [col+1]-vertGridLines [col],
                                       horzGridLines [row+1]-horzGridLines [row]);
    // if a number of adjacent components have been selected and the component being dragged
    // is not the leftmost, we return the column in which the leftmost component should be dropped
    if ((mode == GridInsertLocation.GridInsertMode.RowBefore || mode == GridInsertLocation.GridInsertMode.RowAfter) &&
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
    //noinspection EnumSwitchStatementWhichMissesCases
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
    final GridInsertLocation insertLocation = getGridInsertLocation(myEditor, x, y, dragColumnDelta);
    if (insertLocation.getContainer() != null) {
      if (isDropInsertAllowed(insertLocation, componentCount)) {
        placeInsertFeedbackPainter(insertLocation, componentCount);
        return copyOnDrop ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE;
      }
    }
    if (componentCount > 0) {
      final RadContainer containerAt = FormEditingUtil.getRadContainerAt(myEditor, x, y, 0);
      if (containerAt != null) {
        final Point targetPoint = SwingUtilities.convertPoint(myEditor.getDragLayer(), x, y, containerAt.getDelegee());
        if (containerAt.canDrop(targetPoint.x, targetPoint.y, componentCount)) {
          Rectangle feedbackRect = containerAt.getDropFeedbackRectangle(targetPoint.x, targetPoint.y, componentCount);
          if (feedbackRect != null) {
            final Rectangle rc = SwingUtilities.convertRectangle(containerAt.getDelegee(),
                                                                 feedbackRect,
                                                                 myEditor.getActiveDecorationLayer());
            myEditor.getActiveDecorationLayer().putFeedback(rc);
          }
          else {
            myEditor.getActiveDecorationLayer().removeFeedback();
          }
          return copyOnDrop ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE;
        }
        else {
          myEditor.getActiveDecorationLayer().removeFeedback();
        }
      }
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
    if (insertLocation.getMode() == GridInsertLocation.GridInsertMode.None) {
      return componentCount == 1 &&
             insertLocation.getContainer().getComponentAtGrid(insertLocation.getRow(), insertLocation.getColumn()) == null;
    }
    final GridLayoutManager grid = ((GridLayoutManager)insertLocation.getContainer().getLayout());
    return insertLocation.getColumn() + componentCount - 1 < grid.getColumnCount();
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

    FeedbackPainter painter = (insertLocation.getMode() == GridInsertLocation.GridInsertMode.ColumnBefore ||
                               insertLocation.getMode() == GridInsertLocation.GridInsertMode.ColumnAfter)
      ? myVertInsertFeedbackPainter
      : myHorzInsertFeedbackPainter;
    Rectangle rc;

    int w=4;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (insertLocation.getMode()) {
      case ColumnBefore:
        rc = new Rectangle(cellRect.x - w, cellRect.y - INSERT_ARROW_SIZE,
                           2 * w, cellRect.height + 2 * INSERT_ARROW_SIZE);
        break;

      case ColumnAfter:
        rc = new Rectangle((int)cellRect.getMaxX() - w, (int)cellRect.getMinY() - INSERT_ARROW_SIZE,
                           2 * w, (int)cellRect.getHeight() + 2 * INSERT_ARROW_SIZE);
        break;

      case RowBefore:
        rc = new Rectangle((int)cellRect.getMinX() - INSERT_ARROW_SIZE, (int)cellRect.getMinY() - w,
                           (int)cellRect.getWidth() + 2 * INSERT_ARROW_SIZE, 2 * w);
        break;

      case RowAfter:
        rc = new Rectangle((int)cellRect.getMinX() - INSERT_ARROW_SIZE, (int)cellRect.getMaxY() - w,
                           (int)cellRect.getWidth() + 2 * INSERT_ARROW_SIZE, 2 * w);
        break;

      default:
        rc = cellRect;
        painter = null;
    }
    myEditor.getActiveDecorationLayer().putFeedback(rc, painter);
  }
}
