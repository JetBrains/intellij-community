/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;

import javax.swing.*;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Component;

/**
 * @author yole
 */
public class RadBoxLayoutManager extends RadGridLayoutManager {
  private boolean myHorizontal = false;
  private int myComponentIndex = 0;

  @Override
  public void createSnapshotLayout(final SnapshotContext context,
                                   final JComponent parent,
                                   final RadContainer container,
                                   final LayoutManager layout) {
    int rowCount = 1;
    int colCount = 1;

    boolean hasFiller = false;
    // workaround for lack of BoxLayout.getAxis()
    if (parent.getComponentCount() > 1) {
      for(Component component: parent.getComponents()) {
        if (component instanceof Box.Filler) {
          hasFiller = true;
        }
      }

      Rectangle bounds1 = parent.getComponent(0).getBounds();
      Rectangle bounds2 = parent.getComponent(1).getBounds();
      if (bounds2.x >= bounds1.x + bounds1.width) {
        colCount = parent.getComponentCount();
        if (!hasFiller) colCount++;
        myHorizontal = true;
      }
      else {
        rowCount = parent.getComponentCount();
        if (!hasFiller) rowCount++;
      }
    }
    container.setLayout(new GridLayoutManager(rowCount, colCount));
    if (!hasFiller) {
      if (myHorizontal) {
        container.addComponent(new RadHSpacer(context.newId(), colCount-1));
      }
      else {
        container.addComponent(new RadVSpacer(context.newId(), rowCount-1));
      }
    }
  }


  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    if (myHorizontal) {
      component.getConstraints().setColumn(myComponentIndex);
    }
    else {
      component.getConstraints().setRow(myComponentIndex);
    }
    myComponentIndex++;
    component.getConstraints().setFill(GridConstraints.FILL_BOTH);
    container.addComponent(component);
  }
}
