/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.NoDropLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Design-time support for a layout manager.
 *
 * @author yole
 */
public abstract class RadLayoutManager {
  /**
   * Returns the name of the layout manager. If null is returned, the layout manager property is not
   * shown by the user.
   *
   * @return the layout manager name.
   */
  @Nullable public abstract String getName();

  @Nullable public LayoutManager createLayout() {
    return null;
  }

  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    container.setLayoutManager(this);
  }

  public abstract void writeChildConstraints(final XmlWriter writer, final RadComponent child);

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
  }

  @NotNull public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    return new NoDropLocation();
  }

  public abstract void addComponentToContainer(final RadContainer container, final RadComponent component, final int index);

  public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
    container.getDelegee().remove(component.getDelegee());
  }

  public boolean isSwitchedToChild(RadContainer container, RadComponent child) {
    return true;
  }

  public boolean switchContainerToChild(RadContainer container, RadComponent child) {
    return false;
  }

  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY;
  }

  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return Property.EMPTY_ARRAY;
  }

  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    throw new UnsupportedOperationException("Layout manager " + this + " does not support adding snapshot components");
  }

  public void createSnapshotLayout(final SnapshotContext context,
                                   final JComponent parent,
                                   final RadContainer container,
                                   final LayoutManager layout) {
  }

  public boolean isIndexed() {
    return false;
  }

  public boolean isGrid() {
    return false;
  }

  @Nullable
  public RadComponent getComponentAtGrid(RadContainer container, final int row, final int column) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int getGridRowCount(RadContainer container) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int getGridColumnCount(RadContainer container) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int getGridRowAt(RadContainer container, int y) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int getGridColumnAt(RadContainer container, int x) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public Rectangle getGridCellRangeRect(RadContainer container, int startRow, int startCol, int endRow, int endCol) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int[] getHorizontalGridLines(RadContainer container) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int[] getVerticalGridLines(RadContainer container) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int[] getGridCellCoords(RadContainer container, boolean isRow) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public int[] getGridCellSizes(RadContainer container, boolean isRow) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  @Nullable
  public RowColumnPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
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

  @Nullable
  public ActionGroup getCaptionActions() {
    return null;
  }

  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int i, final Graphics2D g2d,
                                     final Rectangle rc) {
  }

  /**
   * @return the number of inserted rows or columns
   */
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }

  public void deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    throw new UnsupportedOperationException("Not a grid layout manager");
  }
}
