/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadGridBagLayoutManager extends RadGridLayoutManager {
  private int myLastSnapshotRow = -1;
  private int myLastSnapshotCol = -1;
  private int[] mySnapshotXMax = new int[512];
  private int[] mySnapshotYMax = new int[512];

  @Override public String getName() {
    return UIFormXmlConstants.LAYOUT_GRIDBAG;
  }

  @Override
  public void createSnapshotLayout(final SnapshotContext context,
                                   final JComponent parent,
                                   final RadContainer container,
                                   final LayoutManager layout) {
    Dimension gridSize = getGridBagSize(parent, layout);

    boolean haveHGrow = false;
    boolean haveVGrow = false;
    GridBagLayout gridBag = (GridBagLayout) layout;
    for(Component component: parent.getComponents()) {
      final GridBagConstraints constraints = gridBag.getConstraints(component);
      if (constraints.weightx > 0.01) haveHGrow = true;
      if (constraints.weighty > 0.01) haveVGrow = true;
    }

    if (!haveHGrow) {
      gridSize.width++;
    }
    if (!haveVGrow) {
      gridSize.height++;
    }

    container.setLayout(new GridLayoutManager(gridSize.height, gridSize.width));

    if (!haveHGrow) {
      container.addComponent(new RadHSpacer(context.newId(), gridSize.width-1));
    }
    if (!haveVGrow) {
      container.addComponent(new RadVSpacer(context.newId(), gridSize.height-1));
    }
  }

  public static Dimension getGridBagSize(final JComponent parent, final LayoutManager layout) {
    GridBagLayout gridBag = (GridBagLayout) layout;
    int[][] layoutDimensions = gridBag.getLayoutDimensions();

    int rowCount = layoutDimensions[1].length;
    int colCount = layoutDimensions[0].length;

    // account for invisible components
    for(Component component: parent.getComponents()) {
      final GridBagConstraints constraints = gridBag.getConstraints(component);
      colCount = Math.max(colCount, constraints.gridx + constraints.gridwidth);
      rowCount = Math.max(rowCount, constraints.gridy + constraints.gridheight);
    }

    return new Dimension(colCount, rowCount);
  }

  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();

    // logic copied from GridBagLayout.java

    GridBagLayout gridBag = (GridBagLayout) parent.getLayout();
    final GridBagConstraints constraints = gridBag.getConstraints(child);

    int curX = constraints.gridx;
    int curY = constraints.gridy;
    int curWidth = constraints.gridwidth;
    int curHeight = constraints.gridheight;
    int px;
    int py;

    /* If x or y is negative, then use relative positioning: */
    if (curX < 0 && curY < 0) {
      if(myLastSnapshotRow >= 0)
        curY = myLastSnapshotRow;
      else if(myLastSnapshotCol >= 0)
        curX = myLastSnapshotCol;
      else
        curY = 0;
    }

    if (curX < 0) {
      if (curHeight <= 0) {
        curHeight += grid.getRowCount() - curY;
        if (curHeight < 1)
          curHeight = 1;
      }

      px = 0;
      for (int i = curY; i < (curY + curHeight); i++)
        px = Math.max(px, mySnapshotXMax[i]);

      curX = px - curX - 1;
      if(curX < 0)
        curX = 0;
    }
    else if (curY < 0) {
      if (curWidth <= 0) {
        curWidth += grid.getColumnCount() - curX;
        if (curWidth < 1)
          curWidth = 1;
      }

      py = 0;
      for (int i = curX; i < (curX + curWidth); i++)
        py = Math.max(py, mySnapshotYMax[i]);

      curY = py - curY - 1;
      if(curY < 0)
        curY = 0;
    }

    if (curWidth <= 0) {
      curWidth += grid.getColumnCount() - curX;
      if (curWidth < 1)
        curWidth = 1;
    }

    if (curHeight <= 0) {
      curHeight += grid.getRowCount() - curY;
      if (curHeight < 1)
        curHeight = 1;
    }

    /* Make negative sizes start a new row/column */
    if (constraints.gridheight == 0 && constraints.gridwidth == 0)
      myLastSnapshotRow = myLastSnapshotCol = -1;
    if (constraints.gridheight == 0 && myLastSnapshotRow < 0)
      myLastSnapshotCol = curX + curWidth;
    else if (constraints.gridwidth == 0 && myLastSnapshotCol < 0)
      myLastSnapshotRow = curY + curHeight;

    component.getConstraints().setColumn(curX);
    component.getConstraints().setRow(curY);
    component.getConstraints().setColSpan(curWidth);
    component.getConstraints().setRowSpan(curHeight);

    component.getConstraints().setAnchor(convertAnchor(constraints));
    component.getConstraints().setFill(convertFill(constraints));
    container.addComponent(component);
  }

  private static int convertAnchor(final GridBagConstraints gbc) {
    switch(gbc.anchor) {
      case GridBagConstraints.NORTHWEST: return GridConstraints.ANCHOR_NORTHWEST;
      case GridBagConstraints.NORTH:     return GridConstraints.ANCHOR_NORTH;
      case GridBagConstraints.NORTHEAST: return GridConstraints.ANCHOR_NORTHEAST;
      case GridBagConstraints.EAST:      return GridConstraints.ANCHOR_EAST;
      case GridBagConstraints.SOUTHEAST: return GridConstraints.ANCHOR_SOUTHEAST;
      case GridBagConstraints.SOUTH:     return GridConstraints.ANCHOR_SOUTH;
      case GridBagConstraints.SOUTHWEST: return GridConstraints.ANCHOR_SOUTHWEST;
      default:                           return GridConstraints.ANCHOR_WEST;
    }
  }

  private static int convertFill(final GridBagConstraints gbc) {
    switch(gbc.fill) {
      case GridBagConstraints.HORIZONTAL: return GridConstraints.FILL_HORIZONTAL;
      case GridBagConstraints.VERTICAL: return GridConstraints.FILL_VERTICAL;
      case GridBagConstraints.BOTH: return GridConstraints.FILL_BOTH;
      default: return GridConstraints.FILL_NONE;
    }
  }
}
