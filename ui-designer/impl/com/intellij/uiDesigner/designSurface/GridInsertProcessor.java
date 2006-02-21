package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2005
 * Time: 20:10:59
 * To change this template use File | Settings | File Templates.
 */
public class GridInsertProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridInsertProcessor");

  private GuiEditor myEditor;

  public GridInsertProcessor(final GuiEditor editor) {
    myEditor = editor;
  }

  static DropLocation getDropLocation(RadRootContainer rootContainer, Point aPoint, ComponentDragObject dragObject) {
    int EPSILON = 4;
    RadContainer container = FormEditingUtil.getRadContainerAt(rootContainer, aPoint.x, aPoint.y, EPSILON);
    // to facilitate initial component adding, increase stickiness if there is one container at top level
    if (container instanceof RadRootContainer && rootContainer.getComponentCount() == 1) {
      final RadComponent singleComponent = rootContainer.getComponents()[0];
      if (singleComponent instanceof RadContainer) {
        Rectangle rc = singleComponent.getDelegee().getBounds();
        rc.grow(EPSILON*2, EPSILON*2);
        if (rc.contains(aPoint)) {
          container = (RadContainer) singleComponent;
          EPSILON *= 2;
        }
      }
    }

    if (container == null) {
      return new GridDropLocation(false);
    }

    final Point targetPoint = SwingUtilities.convertPoint(rootContainer.getDelegee(), aPoint, container.getDelegee());
    DropLocation containerDropLocation = container.getDropLocation(targetPoint);
    if (containerDropLocation != null) {
      return containerDropLocation;
    }

    if (!container.isGrid()) {
      return new GridDropLocation(container, targetPoint, container.canDrop(targetPoint, dragObject));
    }

    final GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    if (grid.getRowCount() == 1 && grid.getColumnCount() == 1 &&
      container.getComponentAtGrid(0, 0) == null) {
      final Rectangle rc = grid.getCellRangeRect(0, 0, 0, 0);
      return new FirstComponentInsertLocation(container, 0, 0, targetPoint, rc);
    }

    int[] xs = grid.getXs();
    int[] ys = grid.getYs();
    int[] widths = grid.getWidths();
    int[] heights = grid.getHeights();

    int[] horzGridLines = grid.getHorizontalGridLines();
    int[] vertGridLines = grid.getVerticalGridLines();

    int row=ys.length-1;
    int col=xs.length-1;
    for(int i=0; i<xs.length; i++) {
      if (targetPoint.x < xs[i] + widths[i]) {
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

    GridInsertMode mode = GridInsertMode.InCell;

    int dy = (int)(targetPoint.getY() - ys [row]);
    if (dy < EPSILON) {
      mode = GridInsertMode.RowBefore;
    }
    else if (heights [row] - dy < EPSILON) {
      mode = GridInsertMode.RowAfter;
    }

    int dx = targetPoint.x - xs[col];
    if (dx < EPSILON) {
      mode = GridInsertMode.ColumnBefore;
    }
    else if (widths [col] - dx < EPSILON) {
      mode = GridInsertMode.ColumnAfter;
    }

    final int cellWidth = vertGridLines[col + 1] - vertGridLines[col];
    final int cellHeight = horzGridLines[row + 1] - horzGridLines[row];
    if (mode == GridInsertMode.InCell) {
      RadComponent component = container.getComponentAtGrid(row, col);
      if (component != null) {
        Rectangle rc = component.getBounds();
        rc.translate(-xs [col], -ys [row]);

        int right = rc.x + rc.width + GridInsertLocation.INSERT_RECT_MIN_SIZE;
        int bottom = rc.y + rc.height + GridInsertLocation.INSERT_RECT_MIN_SIZE;

        if (dy < rc.y - GridInsertLocation.INSERT_RECT_MIN_SIZE) {
          mode = GridInsertMode.RowBefore;
        }
        else if (dy > bottom && dy < cellHeight) {
          mode = GridInsertMode.RowAfter;
        }
        if (dx < rc.x - GridInsertLocation.INSERT_RECT_MIN_SIZE) {
          mode = GridInsertMode.ColumnBefore;
        }
        else if (dx > right && dx < cellWidth) {
          mode = GridInsertMode.ColumnAfter;
        }
      }
    }

    Rectangle cellRect = new Rectangle(vertGridLines [col],
                                       horzGridLines [row],
                                       cellWidth,
                                       cellHeight);

    if (mode == GridInsertMode.RowBefore || mode == GridInsertMode.RowAfter ||
        mode == GridInsertMode.ColumnBefore || mode == GridInsertMode.ColumnAfter) {
      return new GridInsertLocation(container, row, col, targetPoint, cellRect, mode);
    }
    return new GridDropLocation(container, row, col, targetPoint, cellRect);
  }

  public DropLocation processDragEvent(Point pnt, ComponentDragObject dragObject) {
    final DropLocation dropLocation = getDropLocation(myEditor.getRootContainer(), pnt, dragObject);
    LOG.info("GridInsertProcessor.processDragEvent(): dropLocation " + dropLocation.toString());
    if (dropLocation.canDrop(dragObject)) {
      dropLocation.placeFeedback(myEditor.getActiveDecorationLayer(), dragObject);
    }
    else {
      myEditor.getActiveDecorationLayer().removeFeedback();
    }

    return dropLocation;
  }

  public Cursor processMouseMoveEvent(final Point pnt, final boolean copyOnDrop, final ComponentDragObject dragObject) {
    DropLocation location = processDragEvent(pnt, dragObject);
    if (!location.canDrop(dragObject)) {
      return FormEditingUtil.getMoveNoDropCursor();
    }
    return copyOnDrop ? FormEditingUtil.getCopyDropCursor() : FormEditingUtil.getMoveDropCursor();
  }
}
