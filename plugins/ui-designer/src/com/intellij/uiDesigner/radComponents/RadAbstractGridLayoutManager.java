/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class RadAbstractGridLayoutManager extends RadLayoutManager {
  protected final Map<RadComponent, MyPropertyChangeListener> myListenerMap = new HashMap<>();

  @Override
  public boolean isGrid() {
    return true;
  }

  public abstract int getGridRowCount(RadContainer container);
  public abstract int getGridColumnCount(RadContainer container);

  public int getGridCellCount(RadContainer container, boolean isRow) {
    return isRow ? getGridRowCount(container) : getGridColumnCount(container);
  }

  public int getGridRowAt(RadContainer container, int y) {
    return getGridCellAt(container, y, true);
  }

  public int getGridColumnAt(RadContainer container, int x) {
    return getGridCellAt(container, x, false);
  }

  private int getGridCellAt(final RadContainer container, final int coord, final boolean isRow) {
    int[] coords = getGridCellCoords(container, isRow);
    int[] sizes = getGridCellSizes(container, isRow);
    for (int i = 0; i < coords.length; i++) {
      if (coords[i] <= coord && coord <= coords[i] + sizes[i]) {
        return i;
      }
    }
    return -1;
  }

  public abstract int[] getHorizontalGridLines(RadContainer container);
  public abstract int[] getVerticalGridLines(RadContainer container);
  public abstract int[] getGridCellCoords(RadContainer container, boolean isRow);
  public abstract int[] getGridCellSizes(RadContainer container, boolean isRow);

  @Nullable
  public CustomPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
    return null;
  }

  @Nullable
  public static RadComponent getComponentAtGrid(RadContainer container, final int row, final int column) {
    // If the target cell is not empty does not allow drop.
    for(int i=0; i<container.getComponentCount(); i++){
      final RadComponent component = container.getComponent(i);
      if (component.isDragging()) {
        continue;
      }
      final GridConstraints constraints=component.getConstraints();
      if(
        constraints.getRow() <= row && row < constraints.getRow()+constraints.getRowSpan() &&
        constraints.getColumn() <= column && column < constraints.getColumn()+constraints.getColSpan()
      ){
        return component;
      }
    }
    return null;
  }

  public int getGridLineNear(RadContainer container, boolean isRow, Point pnt, int epsilon) {
    int coord = isRow ? pnt.y : pnt.x;
    int[] gridLines = isRow ? getHorizontalGridLines(container) : getVerticalGridLines(container);
    for(int col = 1; col <gridLines.length; col++) {
      if (coord < gridLines [col]) {
        if (coord - gridLines [col-1] < epsilon) {
          return col-1;
        }
        if (gridLines [col] - coord < epsilon) {
          return col;
        }
        return -1;
      }
    }
    if (coord - gridLines [gridLines.length-1] < epsilon) {
      return gridLines.length-1;
    }
    return -1;
  }

  public Rectangle getGridCellRangeRect(RadContainer container, int startRow, int startCol, int endRow, int endCol) {
    int[] xs = getGridCellCoords(container, false);
    int[] ys = getGridCellCoords(container, true);
    int[] widths = getGridCellSizes(container, false);
    int[] heights = getGridCellSizes(container, true);
    return new Rectangle(xs[startCol],
                         ys[startRow],
                         xs[endCol] + widths[endCol] - xs[startCol],
                         ys[endRow] + heights[endRow] - ys[startRow]);
  }

  public boolean canCellGrow(RadContainer container, boolean isRow, int i) {
    return false;
  }

  @Nullable
  public ActionGroup getCaptionActions() {
    return null;
  }

  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int i, final Graphics2D g,
                                     final Rectangle rc) {
    if (canCellGrow(container, isRow, i)) {
      drawGrowMarker(isRow, g, rc);
    }
  }

  /**
   * @return the number of inserted rows or columns
   */
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow) {
    GridChangeUtil.insertRowOrColumn(grid, cellIndex, isRow, isBefore);
    return 1;
  }

  public void copyGridCells(RadContainer source, final RadContainer destination, final boolean isRow, int cellIndex, int cellCount, int targetIndex) {
    for(int i=0; i< cellCount; i++) {
      insertGridCells(destination, cellIndex, isRow, false, false);
    }
  }

  /**
   * @return the number of deleted rows or columns
   */
  public int deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    GridChangeUtil.deleteCell(grid, cellIndex, isRow);
    return 1;
  }

  public void processCellsMoved(final RadContainer container, final boolean isRow, final int[] cells, final int targetCell) {
    GridChangeUtil.moveCells(container, isRow, cells, targetCell);
  }

  public int getGapCellCount() {
    return 0;
  }

  public int getGapCellSize(final RadContainer container, boolean isRow) {
    return 0;
  }

  public boolean isGapCell(RadContainer grid, boolean isRow, int cellIndex) {
    return false;
  }

  public int getCellIndexBase() {
    return 0;
  }

  public boolean canSpanningAllowed() {
    return true;
  }

  public boolean canResizeCells() {
    return true;
  }

  @Nullable
  public String getCellResizeTooltip(RadContainer container, boolean isRow, int cell, int newSize) {
    return null;
  }
  public void processCellResized(RadContainer container, final boolean isRow, final int cell, final int newSize) {
  }

  public abstract void copyGridSection(final RadContainer source, final RadContainer destination, final Rectangle rc);

  public LayoutManager copyLayout(LayoutManager layout, int rowDelta, int columnDelta) {
    return layout;
  }

  /**
   * Returns true if the dimensions of the grid in the container are defined by the coordinates of the components
   * in the grid (and therefore it must not be validated that an inserted component fits the grid bounds).
   */
  public boolean isGridDefinedByComponents() {
    return false;
  }

  protected static void writeGridConstraints(final XmlWriter writer, final RadComponent child) {
    // Constraints in Grid layout
    writer.startElement("grid");
    try {
      final GridConstraints constraints = child.getConstraints();
      writer.addAttribute("row",constraints.getRow());
      writer.addAttribute("column",constraints.getColumn());
      writer.addAttribute("row-span",constraints.getRowSpan());
      writer.addAttribute("col-span",constraints.getColSpan());
      writer.addAttribute("vsize-policy",constraints.getVSizePolicy());
      writer.addAttribute("hsize-policy",constraints.getHSizePolicy());
      writer.addAttribute("anchor",constraints.getAnchor());
      writer.addAttribute("fill",constraints.getFill());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_INDENT, constraints.getIndent());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_USE_PARENT_LAYOUT, constraints.isUseParentLayout());

      // preferred size
      writer.writeDimension(constraints.myMinimumSize,"minimum-size");
      writer.writeDimension(constraints.myPreferredSize,"preferred-size");
      writer.writeDimension(constraints.myMaximumSize,"maximum-size");
    } finally {
      writer.endElement(); // grid
    }
  }


  @Override @NotNull
  public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    if (container.getGridRowCount() == 1 && container.getGridColumnCount() == 1 &&
        getComponentAtGrid(container, 0, 0) == null) {
      final Rectangle rc = getGridCellRangeRect(container, 0, 0, 0, 0);
      if (location == null) {
        return new FirstComponentInsertLocation(container, rc, 0, 0);
      }
      return new FirstComponentInsertLocation(container, location, rc);
    }

    if (location == null) {
      if (getComponentAtGrid(container, 0, 0) == null) {
        return new GridDropLocation(container, 0, 0);
      }
      return new GridInsertLocation(container, getLastNonSpacerRow(container), 0, GridInsertMode.RowAfter);
    }

    int[] xs = getGridCellCoords(container, false);
    int[] ys = getGridCellCoords(container, true);
    int[] widths = getGridCellSizes(container, false);
    int[] heights = getGridCellSizes(container, true);

    int[] horzGridLines = getHorizontalGridLines(container);
    int[] vertGridLines = getVerticalGridLines(container);

    int row=ys.length-1;
    int col=xs.length-1;
    for(int i=0; i<xs.length; i++) {
      if (location.x < xs[i] + widths[i]) {
        col=i;
        break;
      }
    }
    for(int i=0; i<ys.length; i++) {
      if (location.getY() < ys [i]+heights [i]) {
        row=i;
        break;
      }
    }

    GridInsertMode mode = null;

    int EPSILON = 4;
    int dy = (int)(location.getY() - ys [row]);
    if (dy < EPSILON) {
      mode = GridInsertMode.RowBefore;
    }
    else if (heights [row] - dy < EPSILON) {
      mode = GridInsertMode.RowAfter;
    }

    int dx = location.x - xs[col];
    if (dx < EPSILON) {
      mode = GridInsertMode.ColumnBefore;
    }
    else if (widths [col] - dx < EPSILON) {
      mode = GridInsertMode.ColumnAfter;
    }

    boolean spanInsertMode = canSpanningAllowed() && mode == null;
    boolean normalize = true;
    final int cellWidth = vertGridLines[col + 1] - vertGridLines[col];
    final int cellHeight = horzGridLines[row + 1] - horzGridLines[row];
    if (mode == null) {
      RadComponent component = getComponentAtGrid(container, row, col);
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

        normalize = false;
      }
    }

    if (mode != null) {
      GridInsertLocation dropLocation = new GridInsertLocation(container, row, col, mode);
      dropLocation.setSpanInsertMode(spanInsertMode);
      return normalize ? dropLocation.normalize() : dropLocation;
    }
    if (getComponentAtGrid(container, row, col) instanceof RadVSpacer ||
        getComponentAtGrid(container, row, col) instanceof RadHSpacer) {
      return new GridReplaceDropLocation(container, row, col);
    }
    return new GridDropLocation(container, row, col);
  }

  private int getLastNonSpacerRow(final RadContainer container) {
    int lastRow = getGridRowCount(container)-1;
    for(int col=0; col<getGridColumnCount(container); col++) {
      RadComponent c = getComponentAtGrid(container, lastRow, col);
      if (c != null && !(c instanceof RadHSpacer) && !(c instanceof RadVSpacer)) {
        return lastRow;
      }
    }
    return lastRow-1;
  }

  protected static void drawGrowMarker(final boolean isRow, final Graphics2D g2d, final Rectangle rc) {
    g2d.setColor(Color.BLACK);
    if (!isRow) {
      int maxX = (int) rc.getMaxX();
      int midY = (int) rc.getCenterY()+3;
      final int xStart = Math.max(maxX - 10, rc.x + 2);
      final int xEnd = maxX - 2;
      g2d.drawLine(xStart, midY, xEnd, midY);
      g2d.drawLine(xStart, midY, xStart+2, midY-2);
      g2d.drawLine(xStart, midY, xStart+2, midY+2);
      g2d.drawLine(xEnd, midY, xEnd-2, midY-2);
      g2d.drawLine(xEnd, midY, xEnd-2, midY+2);
    }
    else {
      int maxY = (int) rc.getMaxY();
      int midX = (int) rc.getCenterX()+3;
      final int yStart = Math.max(maxY - 10, rc.y + 2);
      final int yEnd = maxY - 2;
      g2d.drawLine(midX, yStart, midX, yEnd);
      g2d.drawLine(midX, yStart, midX-2, yStart+2);
      g2d.drawLine(midX, yStart, midX+2, yStart+2);
      g2d.drawLine(midX, yEnd, midX-2, yEnd-2);
      g2d.drawLine(midX, yEnd, midX+2, yEnd-2);
    }
  }

  @Override
  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getLayoutManager().isGrid()) {
      RadAbstractGridLayoutManager grid = container.getGridLayoutManager();
      List<Boolean> canRowsGrow = collectCanCellsGrow(grid, container, true);
      List<Boolean> canColumnsGrow = collectCanCellsGrow(grid, container, false);
      List<RadComponent> contents = collectComponents(container);

      changeLayoutFromGrid(container, contents, canRowsGrow, canColumnsGrow);

      int oldGapMultiplier = grid.getGapCellCount()+1;
      int gapMultiplier = getGapCellCount()+1;
      for(RadComponent c: contents) {
        GridConstraints gc = c.getConstraints();
        gc.setRow(gc.getRow() * gapMultiplier / oldGapMultiplier);
        gc.setColumn(gc.getColumn() * gapMultiplier / oldGapMultiplier);
        container.addComponent(c);
      }
    }
    else if (container.getLayoutManager().isIndexed()) {
      List<RadComponent> components = collectComponents(container);
      changeLayoutFromIndexed(container, components);

      int gapMultiplier = getGapCellCount()+1;
      for(int i=0; i<components.size(); i++) {
        GridConstraints gc = components.get(i).getConstraints();
        gc.setRow(0);
        gc.setColumn(i*gapMultiplier);
        gc.setRowSpan(1);
        gc.setColSpan(1);
        container.addComponent(components.get(i));
      }
    }
    else if (container.getComponentCount() == 0) {
      container.setLayoutManager(this);
    }
    else {
      throw new IncorrectOperationException("Cannot change from " + container.getLayout() + " to grid layout");
    }
  }

  protected void changeLayoutFromGrid(final RadContainer container, final List<RadComponent> contents, final List<Boolean> canRowsGrow,
                                    final List<Boolean> canColumnsGrow) {
    container.setLayoutManager(this);
  }

  protected void changeLayoutFromIndexed(final RadContainer container, final List<RadComponent> components) {
    container.setLayoutManager(this);
  }

  private static List<Boolean> collectCanCellsGrow(final RadAbstractGridLayoutManager grid, final RadContainer container, final boolean isRow) {
    List<Boolean> result = new ArrayList<>();
    for(int i=0; i<grid.getGridCellCount(container, isRow); i++) {
      if (!grid.isGapCell(container, isRow, i)) {
        result.add(grid.canCellGrow(container, isRow, i));
      }
    }
    return result;
  }

  private static List<RadComponent> collectComponents(final RadContainer container) {
    List<RadComponent> contents = new ArrayList<>();
    for(int i=container.getComponentCount()-1; i >= 0; i--) {
      final RadComponent component = container.getComponent(i);
      if (!(component instanceof RadHSpacer) && !(component instanceof RadVSpacer)) {
        contents.add(0, component);
      }
      container.removeComponent(component);
    }
    return contents;
  }

  public boolean canMoveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    final int newRow = getNewRow(c, rowDelta);
    final int newCol = getNewColumn(c, colDelta);
    final int newRowSpan = getNewRowSpan(c, rowSpanDelta);
    final int newColSpan = getNewColSpan(c, colSpanDelta);
    if (newRow < 0 || newCol < 0 || newRowSpan < 1 || newColSpan < 1 ||
        newRow + newRowSpan > c.getParent().getGridRowCount() ||
        newCol + newColSpan > c.getParent().getGridColumnCount()) {
      return false;
    }
    c.setDragging(true);
    final RadComponent overlap = c.getParent().findComponentInRect(newRow, newCol, newRowSpan, newColSpan);
    c.setDragging(false);
    if (overlap != null) {
      return false;
    }
    return true;
  }

  private static int getNewRow(final RadComponent c, final int rowDelta) {
    return FormEditingUtil.adjustForGap(c.getParent(), c.getConstraints().getRow() + rowDelta, true, rowDelta);
  }

  private static int getNewColumn(final RadComponent c, final int colDelta) {
    return FormEditingUtil.adjustForGap(c.getParent(), c.getConstraints().getColumn() + colDelta, false, colDelta);
  }

  private static int getNewRowSpan(final RadComponent c, final int rowSpanDelta) {
    int gapCount = c.getParent().getGridLayoutManager().getGapCellCount();
    return c.getConstraints().getRowSpan() + rowSpanDelta * (gapCount+1);
  }

  private static int getNewColSpan(final RadComponent c, final int colSpanDelta) {
    int gapCount = c.getParent().getGridLayoutManager().getGapCellCount();
    return c.getConstraints().getColSpan() + colSpanDelta * (gapCount+1);
  }

  @Override
  public void moveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    GridConstraints constraints = c.getConstraints();
    GridConstraints oldConstraints = (GridConstraints)constraints.clone();
    constraints.setRow(getNewRow(c, rowDelta));
    constraints.setColumn(getNewColumn(c, colDelta));
    constraints.setRowSpan(getNewRowSpan(c, rowSpanDelta));
    constraints.setColSpan(getNewColSpan(c, colSpanDelta));
    c.fireConstraintsChanged(oldConstraints);
  }

  public int getMinCellCount() {
    return 1;
  }

  @Override
  public void addComponentToContainer(RadContainer container, RadComponent component, int index) {
    MyPropertyChangeListener listener = new MyPropertyChangeListener(component);
    myListenerMap.put(component, listener);
    component.addPropertyChangeListener(listener);
  }

  @Override
  public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
    final MyPropertyChangeListener listener = myListenerMap.get(component);
    if (listener != null) {
      component.removePropertyChangeListener(listener);
      myListenerMap.remove(component);
    }
    super.removeComponentFromContainer(container, component);
  }

  protected void updateConstraints(RadComponent component) {
    component.getParent().revalidate();
  }

  private class MyPropertyChangeListener implements PropertyChangeListener {
    private final RadComponent myComponent;

    public MyPropertyChangeListener(final RadComponent component) {
      myComponent = component;
    }

    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(RadComponent.PROP_CONSTRAINTS)) {
        updateConstraints(myComponent);
      }
    }
  }
}
