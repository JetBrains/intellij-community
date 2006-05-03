/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public class RadGridLayoutManager extends RadLayoutManager {
  private GridLayoutColumnProperties myPropertiesPanel;

  public String getName() {
    return UIFormXmlConstants.LAYOUT_INTELLIJ;
  }

  public LayoutManager createLayout() {
    return new GridLayoutManager(1, 1);
  }

  @Override public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getLayout() instanceof GridLayoutManager) {
      GridLayoutManager oldGridLayout = (GridLayoutManager) container.getLayout();
      container.setLayoutManager(this, new GridLayoutManager(oldGridLayout.getRowCount(), oldGridLayout.getColumnCount()));
    }
    else if (container.getLayout() instanceof GridBagLayout) {
      container.setLayoutManager(this,
                                 RadGridBagLayoutManager.gridFromGridBag(container, container.getDelegee(), container.getLayout()));
    }
    else if (container.getLayoutManager().isIndexed()) {
      int col = 0;
      for(RadComponent c: container.getComponents()) {
        c.getConstraints().setRow(0);
        c.getConstraints().setColumn(col++);
        c.getConstraints().setRowSpan(1);
        c.getConstraints().setColSpan(1);
      }
      container.setLayoutManager(this, new GridLayoutManager(1, container.getComponentCount()));
    }
    else {
      throw new IncorrectOperationException("Cannot change from " + container.getLayout() + " to grid layout");
    }
  }

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    GridLayoutManager layout = (GridLayoutManager) radContainer.getLayout();

    writer.addAttribute("row-count", layout.getRowCount());
    writer.addAttribute("column-count", layout.getColumnCount());

    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SAME_SIZE_HORIZONTALLY, layout.isSameSizeHorizontally());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SAME_SIZE_VERTICALLY, layout.isSameSizeVertically());

    RadXYLayoutManager.INSTANCE.writeLayout(writer, radContainer);
  }

  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), component.getConstraints());
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
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

  @Override public Property[] getContainerProperties(final Project project) {
    return new Property[] {
      MarginProperty.getInstance(project),
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project),
      SameSizeHorizontallyProperty.getInstance(project),
      SameSizeVerticallyProperty.getInstance(project)
    };
  }

  @Override public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      HSizePolicyProperty.getInstance(project),
      VSizePolicyProperty.getInstance(project),
      FillProperty.getInstance(project),
      AnchorProperty.getInstance(project),
      RowSpanProperty.getInstance(project),
      ColumnSpanProperty.getInstance(project),
      IndentProperty.getInstance(project),
      UseParentLayoutProperty.getInstance(project),
      MinimumSizeProperty.getInstance(project),
      PreferredSizeProperty.getInstance(project),
      MaximumSizeProperty.getInstance(project)
    };
  }

  @NotNull @Override
  public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    if (container.getGridRowCount() == 1 && container.getGridColumnCount() == 1 &&
        getComponentAtGrid(container, 0, 0) == null) {
      final Rectangle rc = getGridCellRangeRect(container, 0, 0, 0, 0);
      if (location == null) {
        return new FirstComponentInsertLocation(container, 0, 0, rc, 0, 0);
      }
      return new FirstComponentInsertLocation(container, 0, 0, location, rc);
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

    GridInsertMode mode = GridInsertMode.InCell;

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

    final int cellWidth = vertGridLines[col + 1] - vertGridLines[col];
    final int cellHeight = horzGridLines[row + 1] - horzGridLines[row];
    if (mode == GridInsertMode.InCell) {
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
      }
    }

    if (mode == GridInsertMode.RowBefore || mode == GridInsertMode.RowAfter ||
        mode == GridInsertMode.ColumnBefore || mode == GridInsertMode.ColumnAfter) {
      return new GridInsertLocation(container, row, col, mode);
    }
    return new GridDropLocation(container, row, col);
  }

  private int getLastNonSpacerRow(final RadContainer container) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    int lastRow = grid.getRowCount()-1;
    for(int col=0; col<grid.getColumnCount(); col++) {
      RadComponent c = getComponentAtGrid(container, lastRow, col);
      if (c != null && !(c instanceof RadHSpacer) && !(c instanceof RadVSpacer)) {
        return lastRow;
      }
    }
    return lastRow-1;
  }

  @Override public boolean isGrid() {
    return true;
  }

  @Nullable
  public RadComponent getComponentAtGrid(RadContainer container, final int row, final int column) {
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

  @Override public int getGridRowCount(RadContainer container) {
    return ((GridLayoutManager) container.getLayout()).getRowCount();
  }

  @Override public int getGridColumnCount(RadContainer container) {
    return ((GridLayoutManager) container.getLayout()).getColumnCount();
  }

  @Override public int getGridRowAt(RadContainer container, int y) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return grid.getRowAt(y);
  }

  @Override public int getGridColumnAt(RadContainer container, int x) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return grid.getColumnAt(x);
  }

  @Override public Rectangle getGridCellRangeRect(RadContainer container, int startRow, int startCol, int endRow, int endCol) {
    int[] xs = getGridCellCoords(container, false);
    int[] ys = getGridCellCoords(container, true);
    int[] widths = getGridCellSizes(container, false);
    int[] heights = getGridCellSizes(container, true);
    return new Rectangle(xs[startCol],
                         ys[startRow],
                         xs[endCol] + widths[endCol] - xs[startCol],
                         ys[endRow] + heights[endRow] - ys[startRow]);
  }

  @Override public int[] getHorizontalGridLines(RadContainer container) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return grid.getHorizontalGridLines();
  }

  @Override public int[] getVerticalGridLines(RadContainer container) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return grid.getVerticalGridLines();
  }

  public int[] getGridCellCoords(RadContainer container, boolean isRow) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return isRow ? grid.getYs() : grid.getXs();
  }

  public int[] getGridCellSizes(RadContainer container, boolean isRow) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    return isRow ? grid.getHeights() : grid.getWidths();
  }

  @Override
  public RowColumnPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
    if (myPropertiesPanel == null) {
      myPropertiesPanel = new GridLayoutColumnProperties();
    }
    myPropertiesPanel.showProperties(container, isRow, selectedIndices);
    return myPropertiesPanel;
  }

  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int i, final Graphics2D g,
                                     final Rectangle rc) {
    GridLayoutManager layout = (GridLayoutManager) container.getLayout();
    int sizePolicy = layout.getCellSizePolicy(isRow, i);
    if ((sizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(2.0f));
      g.setColor(Color.BLUE);
      if (isRow) {
        int midPoint = (int) rc.getCenterX();
        g.drawLine(midPoint+1, rc.y+1, midPoint+1, rc.y+rc.height-1);
      }
      else {
        int midPoint = (int) rc.getCenterY();
        g.drawLine(rc.x+1, midPoint+1, rc.x+rc.width-1, midPoint+1);
      }
      g.setStroke(oldStroke);
    }
  }

  @Override
  public void insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore) {
    GridChangeUtil.insertRowOrColumn(grid, cellIndex, isRow, isBefore);
  }

  @Override
  public void deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    GridChangeUtil.deleteCell(grid, cellIndex, isRow);
  }
}
