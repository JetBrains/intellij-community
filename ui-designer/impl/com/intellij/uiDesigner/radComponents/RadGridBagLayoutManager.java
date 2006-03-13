/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;

/**
 * @author yole
 */
public class RadGridBagLayoutManager extends RadGridLayoutManager {

  @Override public String getName() {
    return UIFormXmlConstants.LAYOUT_GRIDBAG;
  }

  @Override
  public void createSnapshotLayout(final RadContainer container, final LayoutManager layout) {
    GridBagLayout gridBag = (GridBagLayout) layout;
    int[][] layoutDimensions = gridBag.getLayoutDimensions();
    container.setLayout(new GridLayoutManager(layoutDimensions [1].length,
                                              layoutDimensions [0].length));
  }

  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    GridBagLayout gridBag = (GridBagLayout) parent.getLayout();
    final GridBagConstraints gbc = gridBag.getConstraints(child);
    component.getConstraints().setColumn(gbc.gridx);
    component.getConstraints().setRow(gbc.gridy);
    component.getConstraints().setColSpan(gbc.gridwidth);
    component.getConstraints().setRowSpan(gbc.gridheight);
    component.getConstraints().setAnchor(convertAnchor(gbc));
    component.getConstraints().setFill(convertFill(gbc));
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
